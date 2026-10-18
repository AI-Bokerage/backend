import type { DomainStatus, DomainVerificationMethod } from "../../common/types.ts";

export interface TenantDomainRecord {
  id: string;
  tenantId: string;
  domain: string;
  verificationToken: string;
  status: DomainStatus;
  verificationMethod: DomainVerificationMethod;
  verifiedAt: Date | null;
  lastCheckedAt: Date | null;
  isPrimary: boolean;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreateDomainInput {
  tenantId: string;
  domain: string;
  verificationToken: string;
  verificationMethod: DomainVerificationMethod;
}

export interface UpdateDomainStatusFields {
  verifiedAt?: Date;
  lastCheckedAt?: Date;
}

export interface ITenantDomainRepo {
  create(input: CreateDomainInput): Promise<TenantDomainRecord>;
  findById(id: string): Promise<TenantDomainRecord | null>;
  findByDomain(domain: string): Promise<TenantDomainRecord | null>;
  listByTenant(tenantId: string, opts?: { includeDetached?: boolean }): Promise<TenantDomainRecord[]>;
  updateStatus(id: string, status: DomainStatus, fields?: UpdateDomainStatusFields): Promise<TenantDomainRecord>;
  setPrimary(tenantId: string, id: string): Promise<TenantDomainRecord>;
  touchLastChecked(id: string, at: Date): Promise<void>;
}
