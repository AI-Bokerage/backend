import type { Redis } from "ioredis";
import type { PrismaClient, Prisma, RevenueSnapshot } from "../../../infra/persistence/prisma-client.ts";
import type { TenantMetricsPort } from "../../../domain/ports/tenant-metrics.port.ts";
import type { UsgClientPort, UsgUsageSummary } from "../../../domain/ports/usg-client.port.ts";
import { TenantMetricsUnavailableError, UsgClientError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import type {
  RevenueSnapshotDto,
  RevenueFilter,
  RevenueHistoryQuery,
  RevenueHistoryEntry,
  SnapshotPeriod,
  PlanDistributionEntry,
  RevenueBreakdownByPlan,
  RevenueBreakdownByRegion,
  UsageAcrossTenantsResult,
} from "./types.ts";

const CACHE_KEY = "sup:analytics:revenue";
const TRIAL_CONVERSION_WINDOW_MS = 30 * 86_400_000; // default trailing 30-day window when from/to aren't supplied

function withPercentage(dist: Array<{ planCode: string | null; count: number }>): PlanDistributionEntry[] {
  const total = dist.reduce((sum, d) => sum + d.count, 0);
  return dist.map((d) => ({ ...d, percentage: total > 0 ? (d.count / total) * 100 : 0 }));
}

function toHistoryEntry(row: RevenueSnapshot): RevenueHistoryEntry {
  return {
    id: row.id,
    period: row.period,
    capturedAt: row.capturedAt.toISOString(),
    totalMrr: row.totalMrr,
    totalArr: row.totalArr,
    averageRevenuePerTenant: row.averageRevenuePerTenant,
    // Prisma's Json columns are typed as Prisma.JsonValue (a recursive union),
    // which doesn't structurally overlap enough with a specific array-of-DTO
    // type for a single `as` cast — TS requires the `unknown` intermediary.
    byPlan: row.byPlan as unknown as RevenueBreakdownByPlan[],
    byRegion: row.byRegion as unknown as RevenueBreakdownByRegion[],
    planDistribution: row.planDistribution as unknown as PlanDistributionEntry[],
  };
}

export class AnalyticsService {
  constructor(
    private readonly port: TenantMetricsPort,
    private readonly valkey: Redis,
    private readonly cacheTtlSec: number,
    private readonly prisma: PrismaClient,
    private readonly usgClient: UsgClientPort,
  ) {}

  private async computeRevenueSnapshot(filter: RevenueFilter): Promise<RevenueSnapshotDto> {
    const now = new Date();
    const to = filter.to ?? now;
    const from = filter.from ?? new Date(to.getTime() - TRIAL_CONVERSION_WINDOW_MS);

    let totalMrr: number;
    let activeCount: number;
    let byPlan: RevenueBreakdownByPlan[];
    let byRegion: RevenueBreakdownByRegion[];
    let planDistributionRaw: Array<{ planCode: string | null; count: number }>;
    let trialConversionRaw: { trials: number; converted: number };
    try {
      [totalMrr, activeCount, byPlan, byRegion, planDistributionRaw, trialConversionRaw] = await Promise.all([
        this.port.sumActiveAndTrialMrr(),
        this.port.countActive(),
        this.port.mrrByPlan(filter.region ? { region: filter.region } : undefined),
        this.port.mrrByRegion(filter.planCode ? { planCode: filter.planCode } : undefined),
        this.port.planDistribution(),
        this.port.trialConversion(from, to),
      ]);
    } catch (err) {
      throw new TenantMetricsUnavailableError(err);
    }

    const averageRevenuePerTenant = activeCount > 0 ? totalMrr / activeCount : 0;
    const rate = trialConversionRaw.trials > 0 ? trialConversionRaw.converted / trialConversionRaw.trials : 0;

    return {
      totalMrr,
      totalArr: totalMrr * 12,
      averageRevenuePerTenant,
      byPlan,
      byRegion,
      planDistribution: withPercentage(planDistributionRaw),
      trialConversion: { ...trialConversionRaw, rate },
      generatedAt: new Date().toISOString(),
    };
  }

  // Only the unfiltered (whole-fleet, no planCode/region/from/to) snapshot is
  // cached — a filtered/date-ranged query is computed fresh every time,
  // avoiding an explosion of per-filter cache key permutations.
  async getRevenueSnapshot(filter: RevenueFilter = {}): Promise<RevenueSnapshotDto> {
    const cacheable = !filter.planCode && !filter.region && !filter.from && !filter.to;

    if (cacheable) {
      try {
        const cached = await this.valkey.get(CACHE_KEY);
        if (cached) return JSON.parse(cached) as RevenueSnapshotDto;
      } catch (err) {
        logger.warn({ err }, "[Analytics] Valkey read failed — computing live");
      }
    }

    const snapshot = await this.computeRevenueSnapshot(filter);

    if (cacheable) {
      try {
        await this.valkey.set(CACHE_KEY, JSON.stringify(snapshot), "EX", this.cacheTtlSec);
      } catch (err) {
        logger.warn({ err }, "[Analytics] Valkey write failed — snapshot not cached");
      }
    }

    return snapshot;
  }

  async captureRevenueSnapshot(period: SnapshotPeriod): Promise<RevenueHistoryEntry> {
    const snapshot = await this.computeRevenueSnapshot({});
    const row = (await this.prisma.revenueSnapshot.create({
      data: {
        period,
        totalMrr: snapshot.totalMrr,
        totalArr: snapshot.totalArr,
        averageRevenuePerTenant: snapshot.averageRevenuePerTenant,
        // Same unknown-intermediary reasoning as toHistoryEntry above, in
        // reverse: Prisma.InputJsonValue's InputJsonObject requires a string
        // index signature our DTO arrays don't structurally have.
        byPlan: snapshot.byPlan as unknown as Prisma.InputJsonValue,
        byRegion: snapshot.byRegion as unknown as Prisma.InputJsonValue,
        planDistribution: snapshot.planDistribution as unknown as Prisma.InputJsonValue,
      },
    })) as RevenueSnapshot;
    return toHistoryEntry(row);
  }

  async getRevenueHistory(query: RevenueHistoryQuery): Promise<RevenueHistoryEntry[]> {
    const where: { period: string; capturedAt?: { gte?: Date; lte?: Date } } = { period: query.period };
    if (query.from || query.to) {
      where.capturedAt = {};
      if (query.from) where.capturedAt.gte = query.from;
      if (query.to) where.capturedAt.lte = query.to;
    }
    const rows = (await this.prisma.revenueSnapshot.findMany({
      where,
      orderBy: { capturedAt: "asc" },
    })) as RevenueSnapshot[];
    return rows.map(toHistoryEntry);
  }

  async getUsageAcrossTenants(tenantIds: string[]): Promise<UsageAcrossTenantsResult> {
    const tenants: UsgUsageSummary[] = [];
    const failures: Array<{ tenantId: string; error: string }> = [];

    for (const tenantId of tenantIds) {
      try {
        const summary = await this.usgClient.getSummary(tenantId);
        tenants.push(summary);
      } catch (err) {
        const message = err instanceof Error ? err.message : String(err);
        failures.push({ tenantId, error: message });
        logger.warn({ err, tenantId }, "[Analytics] getSummary failed for tenant — continuing");
      }
    }

    if (tenantIds.length > 0 && tenants.length === 0) {
      throw new UsgClientError(`all ${tenantIds.length} tenant usage lookups failed`);
    }

    const totals: Record<string, number> = {};
    for (const t of tenants) {
      for (const m of t.meters) {
        totals[m.metric] = (totals[m.metric] ?? 0) + m.current;
      }
    }

    return { tenants, failures, totals };
  }
}
