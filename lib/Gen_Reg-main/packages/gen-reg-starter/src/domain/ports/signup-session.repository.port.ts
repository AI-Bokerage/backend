// src/domain/ports/signup-session.repository.port.ts
import type { SignupState } from "../enums/signup-state.enum.ts";

export interface SignupSessionRecord {
  id: string;
  email: string;
  companyName: string | null;
  fullName: string | null;
  phone: string | null;
  source: string | null;
  referralCode: string | null;
  utmSource: string | null;
  utmMedium: string | null;
  utmCampaign: string | null;
  desiredSubdomain: string;
  state: SignupState;
  emailVerificationTokenHash: string | null;
  emailVerifiedAt: Date | null;
  resumeTokenHash: string | null;
  authUserId: string;
  selectedPlanCode: string | null;
  selectedBillingCycle: string | null;
  paymentProvider: string | null;
  checkoutSessionId: string | null;
  paymentCustomerId: string | null;
  provisioningJobId: string | null;
  provisionedTenantId: string | null;
  lastProvisioningError: string | null;
  expiresAt: Date;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreateSignupSessionInput {
  email: string;
  companyName?: string;
  fullName?: string;
  phone?: string;
  source?: string;
  referralCode?: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  desiredSubdomain: string;
  authUserId: string;
  emailVerificationTokenHash: string;
  resumeTokenHash: string;
  expiresAt: Date;
}

export interface ISignupSessionRepo {
  create(input: CreateSignupSessionInput): Promise<SignupSessionRecord>;
  findById(id: string): Promise<SignupSessionRecord | null>;
  findByEmailVerificationTokenHash(hash: string): Promise<SignupSessionRecord | null>;
  findByResumeTokenHash(hash: string): Promise<SignupSessionRecord | null>;
  existsActiveForEmail(email: string): Promise<boolean>;
  existsActiveForSubdomain(subdomain: string): Promise<boolean>;
  updateState(id: string, state: SignupState, patch?: Partial<SignupSessionRecord>): Promise<SignupSessionRecord>;
  findExpiredInStates(states: SignupState[], now: Date): Promise<SignupSessionRecord[]>;
  findInState(state: SignupState): Promise<SignupSessionRecord[]>;
}
