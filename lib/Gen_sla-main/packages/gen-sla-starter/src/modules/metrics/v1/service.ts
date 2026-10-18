import type { IMetricsRepo } from "../../../domain/ports/metrics-repo.port.ts";
import type { MetricsQuery, TrendsQuery } from "./schema.ts";
import type { SlaSummary, SlaComplianceRate, SlaBreachRecord, SlaTrendPoint } from "./types.ts";

function toFilters(query: MetricsQuery) {
  return { entityType: query.entityType, from: query.from ? new Date(query.from) : undefined, to: query.to ? new Date(query.to) : undefined };
}

function formatDateKey(date: Date, granularity: string): string {
  const d = new Date(date);
  if (granularity === "month") return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
  if (granularity === "week") {
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1);
    const monday = new Date(d.setDate(diff));
    return monday.toISOString().slice(0, 10);
  }
  return d.toISOString().slice(0, 10);
}

export class MetricsService {
  constructor(private readonly repo: IMetricsRepo) {}

  async getSummary(tenantId: string, query: MetricsQuery): Promise<SlaSummary> {
    const counts = await this.repo.getSummary(tenantId, toFilters(query));
    return {
      totalActive: counts.ACTIVE,
      totalWarning: counts.WARNING,
      totalBreached: counts.BREACHED,
      totalResolved: counts.RESOLVED,
      totalCancelled: counts.CANCELLED,
    };
  }

  async getComplianceRates(tenantId: string, query: MetricsQuery): Promise<SlaComplianceRate[]> {
    const rows = await this.repo.getComplianceRates(tenantId, toFilters(query));
    return rows.map((r) => ({
      ...r,
      compliancePct: r.total > 0 ? Math.round(((r.total - r.breached) / r.total) * 100 * 10) / 10 : 100,
    }));
  }

  async getBreaches(tenantId: string, query: MetricsQuery): Promise<SlaBreachRecord[]> {
    const rows = await this.repo.getRecentBreaches(tenantId, toFilters(query));
    return rows.map((r) => ({
      instanceId: r.id,
      entityType: r.entityType,
      entityId: r.entityId,
      slaType: r.slaType,
      breachedAt: r.breachedAt?.toISOString() ?? "",
      dueAt: r.dueAt.toISOString(),
      overdueMs: r.breachedAt ? r.breachedAt.getTime() - r.dueAt.getTime() : 0,
    }));
  }

  async getTrends(tenantId: string, query: TrendsQuery): Promise<SlaTrendPoint[]> {
    const history = await this.repo.getTrends(tenantId, toFilters(query));
    const pointMap = new Map<string, { created: number; breached: number; resolved: number }>();

    for (const row of history) {
      const dateKey = formatDateKey(row.occurredAt, query.granularity);
      if (!pointMap.has(dateKey)) pointMap.set(dateKey, { created: 0, breached: 0, resolved: 0 });
      const pt = pointMap.get(dateKey)!;
      if (row.toStatus === "ACTIVE") pt.created++;
      if (row.toStatus === "BREACHED") pt.breached++;
      if (row.toStatus === "RESOLVED") pt.resolved++;
    }

    return Array.from(pointMap.entries()).sort(([a], [b]) => a.localeCompare(b)).map(([date, v]) => ({ date, ...v }));
  }
}
