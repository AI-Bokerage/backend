export interface TenantDto {
  id: string;
  slug: string;
  name: string;
  status: string;
  region: string | null;
  primaryOwnerUserId: string;
  provisioningJobId: string | null;
  createdAt: string;
}

export interface CreateTenantResultDto extends TenantDto {
  ownerEmail: string;
  ownerFirstName: string | null;
  ownerLastName: string | null;
}

export interface CreateTenantInput {
  name: string;
  slug: string;
  region: string;
  ownerEmail: string;
  ownerFirstName?: string | null;
  ownerLastName?: string | null;
  idempotencyKey?: string;
}
