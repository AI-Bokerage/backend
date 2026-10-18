import type { Redis } from "ioredis";
import type { TenantMetricsPort } from "../../../domain/ports/tenant-metrics.port.ts";
import { TenantMetricsUnavailableError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import type { DashboardKpis } from "./types.ts";

const CACHE_KEY = "sup:dashboard:kpi";

export class DashboardService {
  constructor(
    private readonly port: TenantMetricsPort,
    private readonly valkey: Redis,
    private readonly cacheTtlSec: number,
  ) {}

  async getKpis(forceRefresh = false): Promise<DashboardKpis> {
    if (!forceRefresh) {
      try {
        const cached = await this.valkey.get(CACHE_KEY);
        if (cached) return JSON.parse(cached) as DashboardKpis;
      } catch (err) {
        logger.warn({ err }, "[Dashboard] Valkey read failed — computing live");
      }
    }

    // Assumes a UTC (or otherwise consistent) server timezone: todayStart/monthStart
    // use server-local midnight, so "today"/"this month" will shift on a non-UTC host.
    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const weekStart = new Date(now.getTime() - 7 * 86_400_000);
    const monthStart = new Date(now.getFullYear(), now.getMonth(), 1);
    const in7Days = new Date(now.getTime() + 7 * 86_400_000);

    let results: [number, number, number, number, number, number, number, number, number, number];
    try {
      results = await Promise.all([
        this.port.countActive(),
        this.port.countSignupsSince(todayStart),
        this.port.countSignupsSince(weekStart),
        this.port.countSignupsSince(monthStart),
        this.port.sumActiveAndTrialMrr(),
        this.port.countActiveTrials(),
        this.port.countTrialsEndingBetween(now, in7Days),
        this.port.countChurnedSince(monthStart),
        this.port.countByStatus("PAST_DUE"),
        this.port.countByStatus("SUSPENDED"),
      ]);
    } catch (err) {
      throw new TenantMetricsUnavailableError(err);
    }

    const [
      activeTenants, signupsToday, signupsThisWeek, signupsThisMonth, mrr,
      activeTrials, trialsEndingIn7d, churnedThisMonth, pastDueCount, suspendedCount,
    ] = results;

    const kpis: DashboardKpis = {
      activeTenants,
      signupsToday,
      signupsThisWeek,
      signupsThisMonth,
      mrr,
      activeTrials,
      trialsEndingIn7d,
      churnedThisMonth,
      pastDueCount,
      suspendedCount,
      generatedAt: new Date().toISOString(),
    };

    try {
      await this.valkey.set(CACHE_KEY, JSON.stringify(kpis), "EX", this.cacheTtlSec);
    } catch (err) {
      logger.warn({ err }, "[Dashboard] Valkey write failed — KPIs not cached");
    }

    return kpis;
  }
}
