export interface InsertReconciliationLogInput {
  tenantId: string;
  metric: string;
  counterValue: bigint;
  dbValue: bigint;
  driftPct: number;
  corrected: boolean;
  runAt: Date;
}

export interface IReconciliationRepo {
  insertLog(entry: InsertReconciliationLogInput): Promise<void>;
}
