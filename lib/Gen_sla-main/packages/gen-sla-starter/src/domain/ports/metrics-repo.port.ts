export interface MetricsFilters {
  entityType?: string;
  from?: Date;
  to?: Date;
}

export interface StatusCounts {
  ACTIVE: number;
  WARNING: number;
  BREACHED: number;
  RESOLVED: number;
  CANCELLED: number;
}

export interface ComplianceRow {
  entityType: string;
  slaType: string;
  total: number;
  breached: number;
  resolved: number;
}

export interface BreachRow {
  id: string;
  entityType: string;
  entityId: string;
  slaType: string;
  breachedAt: Date | null;
  dueAt: Date;
}

export interface TrendRow {
  toStatus: string;
  occurredAt: Date;
}

export interface IMetricsRepo {
  getSummary(tenantId: string, filters: MetricsFilters): Promise<StatusCounts>;
  getComplianceRates(tenantId: string, filters: MetricsFilters): Promise<ComplianceRow[]>;
  getRecentBreaches(tenantId: string, filters: MetricsFilters, limit?: number): Promise<BreachRow[]>;
  getTrends(tenantId: string, filters: MetricsFilters): Promise<TrendRow[]>;
}
