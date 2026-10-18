import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { ILimitProvider } from "../../../domain/ports/limit-provider.port.ts";
import { getMeterDefinition } from "../../meters/v1/registry.ts";
import { readCurrentValue } from "../../meters/v1/keys.ts";
import { GraceOverageService, type GraceWindow } from "../../grace-overage/v1/service.ts";
import { MeterNotRegisteredError } from "../../../common/errors.ts";

export type CheckOutcome = "ALLOW" | "SOFT_WARN_80" | "SOFT_WARN_95" | "GRACE" | "BLOCK";

export interface CheckResult {
  allowed: boolean;
  outcome: CheckOutcome;
  metric: string;
  current: number;
  limit: number;
  pct: number;
  code?: string;
  grace?: GraceWindow;
}

export interface CheckServiceOptions {
  limitCacheTtlSec: number;
  limitLockTtlSec: number;
  softWarnPct80: number;
  softWarnPct95: number;
  graceWindowDays: number;
}

function limitKey(tenantId: string, metric: string): string {
  return `genusg:limit:${tenantId}:${metric}`;
}

function lockKey(tenantId: string): string {
  return `genusg:lock:limit:${tenantId}`;
}

export class CheckService {
  constructor(
    private readonly counterStore: ICounterStore,
    private readonly limitProvider: ILimitProvider,
    private readonly graceOverageService: GraceOverageService,
    private readonly options: CheckServiceOptions,
  ) {}

  /**
   * Resolves every registered meter's limit for a tenant, seeding the cache
   * from ILimitProvider under a stampede lock on a cache miss. Any
   * ILimitProvider failure (throw or the lock-holder never finishing)
   * fails OPEN to Infinity (unlimited) — a deliberate availability-over-
   * strictness choice, not a bug.
   */
  async resolveLimits(tenantId: string, metrics: string[]): Promise<Record<string, number>> {
    const keys = metrics.map((m) => limitKey(tenantId, m));
    const cached = await this.counterStore.mget(keys);
    const result: Record<string, number> = {};
    const missing: string[] = [];
    metrics.forEach((metric, i) => {
      if (cached[i] !== null) {
        result[metric] = cached[i] as number;
      } else {
        missing.push(metric);
      }
    });
    if (missing.length === 0) return result;

    const acquired = await this.counterStore.setNX(lockKey(tenantId), "1", this.options.limitLockTtlSec);
    if (!acquired) {
      await new Promise((r) => setTimeout(r, 100));
      const retry = await this.counterStore.mget(missing.map((m) => limitKey(tenantId, m)));
      missing.forEach((metric, i) => { result[metric] = retry[i] === null ? Infinity : (retry[i] as number); });
      return result;
    }

    try {
      const fetched = await this.limitProvider.getLimits(tenantId);
      const entries = Object.entries(fetched).map(([metric, value]) => ({
        key: limitKey(tenantId, metric),
        value: String(value),
        ttlSec: this.options.limitCacheTtlSec,
      }));
      if (entries.length > 0) await this.counterStore.setBatch(entries);
      missing.forEach((metric) => { result[metric] = fetched[metric] ?? Infinity; });
      return result;
    } catch {
      missing.forEach((metric) => { result[metric] = Infinity; });
      return result;
    } finally {
      await this.counterStore.del(lockKey(tenantId));
    }
  }

  async check(tenantId: string, metric: string, delta = 1): Promise<CheckResult> {
    const def = getMeterDefinition(metric);
    if (!def) throw new MeterNotRegisteredError(metric);

    const [current, limits] = await Promise.all([
      readCurrentValue(this.counterStore, tenantId, metric),
      this.resolveLimits(tenantId, [metric]),
    ]);
    const limit = limits[metric] ?? Infinity;
    const next = current + delta;

    if (limit === -1 || limit === Infinity) {
      return { allowed: true, outcome: "ALLOW", metric, current, limit, pct: 0 };
    }

    const pct = limit === 0 ? 100 : Math.round((next / limit) * 100);

    if (next > limit) {
      if (def.graceEligible) {
        const grace = await this.graceOverageService.getOrOpenGraceWindow(tenantId, metric, this.options.graceWindowDays, new Date());
        return { allowed: true, outcome: "GRACE", metric, current, limit, pct, grace };
      }
      return { allowed: false, outcome: "BLOCK", metric, current, limit, pct, code: "USAGE_QUOTA_EXCEEDED" };
    }

    if (next > limit * this.options.softWarnPct95) {
      return { allowed: true, outcome: "SOFT_WARN_95", metric, current, limit, pct };
    }
    if (next > limit * this.options.softWarnPct80) {
      return { allowed: true, outcome: "SOFT_WARN_80", metric, current, limit, pct };
    }
    return { allowed: true, outcome: "ALLOW", metric, current, limit, pct };
  }
}
