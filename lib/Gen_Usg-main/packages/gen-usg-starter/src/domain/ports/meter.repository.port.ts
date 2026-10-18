export type RollupPeriod = "daily" | "monthly";

export interface UsageSnapshotRecord {
  id: string;
  tenantId: string;
  snapshotAt: Date;
  period: RollupPeriod;
  metrics: Record<string, number>;
  createdAt: Date;
}

export interface IMeterRepo {
  insertSnapshot(tenantId: string, snapshotAt: Date, period: RollupPeriod, metrics: Record<string, number>): Promise<void>;
  findTrend(tenantId: string, sinceDays: number): Promise<UsageSnapshotRecord[]>;
  latestSnapshot(tenantId: string): Promise<UsageSnapshotRecord | null>;
}
