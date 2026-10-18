export interface UsgMeterSummary {
  metric: string;
  unit: string;
  current: number;
  limit: number;
  pct: number;
}

export interface UsgTrendPoint {
  snapshotAt: string;
  period: string;
  metrics: Record<string, number>;
}

export interface UsgUsageSummary {
  tenantId: string;
  meters: UsgMeterSummary[];
  trend: UsgTrendPoint[];
}

export interface UsgClientPort {
  getSummary(tenantId: string): Promise<UsgUsageSummary>;
}
