export interface UsageEventInput {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  reason: string;
  planCode: string | null;
  occurredAt: Date;
}

export interface UsageEventRecord extends UsageEventInput {
  id: string;
}

export interface TelemetryQueryFilter {
  flagKey?: string;
  from?: Date;
  to?: Date;
  page: number;
  pageSize: number;
}

export interface ITelemetryRepo {
  insertMany(events: UsageEventInput[]): Promise<number>;
  query(tenantId: string, filter: TelemetryQueryFilter): Promise<{ events: UsageEventRecord[]; total: number }>;
}
