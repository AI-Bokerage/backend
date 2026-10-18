export interface TntTenantResponse {
  id: string;
  slug: string;
  name: string;
  status: string; // PROVISIONING | ACTIVE | SUSPENDED | CANCELLED | PURGED
  region: string | null;
  primaryOwnerUserId: string;
  provisioningJobId: string | null;
  createdAt: string;
}

export interface CreateTenantParams {
  name: string;
  slug: string;
  region: string;
  primaryOwnerUserId: string;
  idempotencyKey?: string;
}

export interface TntClientPort {
  createTenant(params: CreateTenantParams): Promise<TntTenantResponse>;
  getTenant(id: string): Promise<TntTenantResponse | null>;
  getTenantBySlug(slug: string): Promise<TntTenantResponse | null>;
  suspendTenant(id: string): Promise<TntTenantResponse>;
  reactivateTenant(id: string): Promise<TntTenantResponse>;
}
