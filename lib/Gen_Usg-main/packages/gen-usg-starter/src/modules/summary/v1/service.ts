import type { ICounterStore } from "../../../domain/ports/counter-store.port.ts";
import type { IMeterRepo } from "../../../domain/ports/meter.repository.port.ts";
import type { IGraceOverageRepo, GraceOverageRecord } from "../../../domain/ports/grace-overage.repository.port.ts";
import type { CheckService } from "../../check/v1/service.ts";
import { getMeterDefinition, listRegisteredMeterCodes } from "../../meters/v1/registry.ts";
import { readCurrentValue } from "../../meters/v1/keys.ts";

export interface MeterSummary {
  metric: string;
  unit: string;
  current: number;
  limit: number;
  pct: number;
}

export interface UsageSummary {
  tenantId: string;
  meters: MeterSummary[];
  trend: Array<{ snapshotAt: Date; period: string; metrics: Record<string, number> }>;
  graceOverages: GraceOverageRecord[];
}

export class SummaryService {
  constructor(
    private readonly counterStore: ICounterStore,
    private readonly meterRepo: IMeterRepo,
    private readonly graceOverageRepo: IGraceOverageRepo,
    private readonly checkService: CheckService,
    private readonly trendSinceDays: number,
  ) {}

  async getSummary(tenantId: string): Promise<UsageSummary> {
    const metricCodes = listRegisteredMeterCodes();
    const [currentValues, limits, trend, graceOverages] = await Promise.all([
      Promise.all(metricCodes.map((code) => readCurrentValue(this.counterStore, tenantId, code))),
      this.checkService.resolveLimits(tenantId, metricCodes),
      this.meterRepo.findTrend(tenantId, this.trendSinceDays),
      this.graceOverageRepo.listOpenForTenant(tenantId),
    ]);

    const meters: MeterSummary[] = metricCodes.map((metric, i) => {
      const current = currentValues[i];
      const limit = limits[metric] ?? Infinity;
      const pct = limit === -1 || limit === Infinity || limit === 0 ? 0 : Math.round((current / limit) * 100);
      return { metric, unit: getMeterDefinition(metric)?.unit ?? "unit", current, limit, pct };
    });

    return {
      tenantId,
      meters,
      trend: trend.map((s) => ({ snapshotAt: s.snapshotAt, period: s.period, metrics: s.metrics })),
      graceOverages,
    };
  }
}
