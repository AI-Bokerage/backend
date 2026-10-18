export interface PlatformUser {
  userId: string;
  email: string | null;
}

/** For docs/reference only — gen-sup-starter's own TenantDto types `status` as a plain string, so this does too. */
export type TenantStatus = 'PROVISIONING' | 'ACTIVE' | 'SUSPENDED' | 'CANCELLED' | 'PURGED';

export interface TenantRecord {
  id: string;
  slug: string;
  name: string;
  status: string;
  region: string | null;
  primaryOwnerUserId: string;
  provisioningJobId: string | null;
  createdAt: string;
}

/** "PENDING" until the owner accepts their invite, "ACTIVE" after — separate from Gen_TNT's own infra status. */
export type OnboardingStatus = 'PENDING' | 'ACTIVE';

export interface BrokerageSummary extends TenantRecord {
  onboardingStatus: OnboardingStatus;
  invitationId: string;
}

export interface CreateBrokerageInput {
  name: string;
  ownerName: string;
  ownerEmail: string;
  region: string;
}

export type ExportJobStatus = 'EXPORTING' | 'READY' | 'FAILED' | 'EXPIRED' | 'DELETED';

export interface BrokerageExportJobDto {
  id: string;
  tenantId: string;
  status: ExportJobStatus;
  readyAt: string | null;
  expiresAt: string | null;
}
