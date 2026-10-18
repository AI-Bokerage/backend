// src/domain/ports/tnt-client.port.ts
export interface TenantResponse {
  id: string;
  slug: string;
  name: string;
  status: string;
  region: string | null;
  primaryOwnerUserId: string;
  provisioningJobId: string | null;
  createdAt: string;
}

export interface ProvisioningJobResponse {
  id: string;
  tenantId: string;
  status: string;
  retryCount: number;
  lastError: string | null;
  startedAt: string | null;
  completedAt: string | null;
  expiresAt: string;
}

export interface CreateTenantInput {
  name: string;
  slug: string;
  primaryOwnerUserId: string;
  idempotencyKey: string;
}

export interface ITntClient {
  isSlugTaken(slug: string): Promise<boolean>;
  createTenant(input: CreateTenantInput): Promise<TenantResponse>;
  getTenant(id: string): Promise<TenantResponse>;
  getJob(jobId: string): Promise<ProvisioningJobResponse>;
}
