export interface TenantOverrideRecord {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  config: Record<string, unknown>;
  reason: string | null;
  expiresAt: Date | null;
  createdBy: string | null;
}

export interface UpsertOverrideInput {
  tenantId: string;
  flagKey: string;
  enabled: boolean;
  config?: Record<string, unknown>;
  reason?: string;
  expiresAt?: Date;
  createdBy?: string;
}

export interface ITenantOverrideRepo {
  upsert(input: UpsertOverrideInput): Promise<TenantOverrideRecord>;
  findOne(tenantId: string, flagKey: string): Promise<TenantOverrideRecord | null>;
  findAllForTenant(tenantId: string): Promise<TenantOverrideRecord[]>;
  listAll(filter: { tenantId?: string; flagKey?: string }): Promise<TenantOverrideRecord[]>;
  delete(tenantId: string, flagKey: string): Promise<boolean>;
}
