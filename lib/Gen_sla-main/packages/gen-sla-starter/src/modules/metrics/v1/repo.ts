import type { PrismaClient, Prisma } from "../../../../__generated__/prisma/index.js";
import type { IMetricsRepo, MetricsFilters, StatusCounts, ComplianceRow, BreachRow, TrendRow } from "../../../domain/ports/metrics-repo.port.ts";

function buildWhere(tenantId: string, filters: MetricsFilters): Prisma.SlaInstanceWhereInput {
  const where: Prisma.SlaInstanceWhereInput = { tenantId };
  if (filters.entityType) where.entityType = filters.entityType;
  if (filters.from || filters.to) {
    where.startedAt = { ...(filters.from && { gte: filters.from }), ...(filters.to && { lte: filters.to }) };
  }
  return where;
}

export class PrismaMetricsRepo implements IMetricsRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async getSummary(tenantId: string, filters: MetricsFilters): Promise<StatusCounts> {
    const where = buildWhere(tenantId, filters);
    const groups = await this.prisma.slaInstance.groupBy({ by: ["status"], where, _count: { id: true } });

    const counts: StatusCounts = { ACTIVE: 0, WARNING: 0, BREACHED: 0, RESOLVED: 0, CANCELLED: 0 };
    for (const g of groups) counts[g.status as keyof StatusCounts] = g._count.id;
    return counts;
  }

  async getComplianceRates(tenantId: string, filters: MetricsFilters): Promise<ComplianceRow[]> {
    const where = buildWhere(tenantId, filters);
    const rows = await this.prisma.slaInstance.groupBy({ by: ["entityType", "slaType", "status"], where, _count: { id: true } });

    const map = new Map<string, ComplianceRow>();
    for (const row of rows) {
      const key = `${row.entityType}|${row.slaType}`;
      if (!map.has(key)) map.set(key, { entityType: row.entityType, slaType: row.slaType, total: 0, breached: 0, resolved: 0 });
      const entry = map.get(key)!;
      entry.total += row._count.id;
      if (row.status === "BREACHED") entry.breached += row._count.id;
      if (row.status === "RESOLVED") entry.resolved += row._count.id;
    }
    return Array.from(map.values());
  }

  async getRecentBreaches(tenantId: string, filters: MetricsFilters, limit = 20): Promise<BreachRow[]> {
    const where: Prisma.SlaInstanceWhereInput = { tenantId, status: "BREACHED" };
    if (filters.entityType) where.entityType = filters.entityType;
    if (filters.from || filters.to) {
      where.breachedAt = { ...(filters.from && { gte: filters.from }), ...(filters.to && { lte: filters.to }) };
    }

    return this.prisma.slaInstance.findMany({
      where,
      orderBy: { breachedAt: "desc" },
      take: limit,
      select: { id: true, entityType: true, entityId: true, slaType: true, breachedAt: true, dueAt: true },
    });
  }

  async getTrends(tenantId: string, filters: MetricsFilters): Promise<TrendRow[]> {
    const where = buildWhere(tenantId, filters);
    return this.prisma.slaHistory.findMany({
      where: {
        tenantId,
        ...(filters.from || filters.to ? { occurredAt: { ...(filters.from && { gte: filters.from }), ...(filters.to && { lte: filters.to }) } } : {}),
        toStatus: { in: ["ACTIVE", "BREACHED", "RESOLVED"] },
        instance: where,
      },
      select: { toStatus: true, occurredAt: true },
      orderBy: { occurredAt: "asc" },
    });
  }
}
