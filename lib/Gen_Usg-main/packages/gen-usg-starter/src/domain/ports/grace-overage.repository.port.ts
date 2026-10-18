export type GraceOverageStatus = "OPEN" | "CLOSED";

export interface GraceOverageRecord {
  id: string;
  tenantId: string;
  metric: string;
  graceStartedAt: Date;
  graceExpiresAt: Date;
  overageCount: number;
  status: GraceOverageStatus;
  billedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface IGraceOverageRepo {
  findOpen(tenantId: string, metric: string): Promise<GraceOverageRecord | null>;
  /**
   * Implementations must catch the Postgres unique-violation on
   * (tenantId, metric, status) raised when a concurrent caller wins the
   * @@unique([tenantId, metric, status]) race, and re-read the winning row
   * via findOpen() instead of letting the violation propagate.
   */
  open(tenantId: string, metric: string, graceStartedAt: Date, graceExpiresAt: Date): Promise<GraceOverageRecord>;
  bump(tenantId: string, id: string): Promise<GraceOverageRecord>;
  listOpenForTenant(tenantId: string): Promise<GraceOverageRecord[]>;
  listExpiredOpen(tenantId: string, now: Date): Promise<GraceOverageRecord[]>;
  close(tenantId: string, id: string): Promise<void>;
}
