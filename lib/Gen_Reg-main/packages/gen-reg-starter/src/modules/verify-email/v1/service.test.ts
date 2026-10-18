// src/modules/verify-email/v1/service.test.ts
import { describe, it, expect, vi } from "vitest";
import { VerifyEmailService } from "./service.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import {
  EmailVerificationTokenInvalidError,
  EmailAlreadyVerifiedError,
} from "../../../common/errors.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "../../../domain/ports/signup-session.repository.port.ts";

function baseSession(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "session-1", email: "founder@example.com", companyName: "Acme", fullName: null, phone: null,
    source: null, referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null,
    desiredSubdomain: "acme", state: SignupState.STARTED,
    emailVerificationTokenHash: "hash-1", emailVerifiedAt: null, resumeTokenHash: "resume-1",
    authUserId: "auth-user-1", selectedPlanCode: null, selectedBillingCycle: null,
    paymentProvider: null, checkoutSessionId: null, paymentCustomerId: null,
    provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
    expiresAt: new Date(Date.now() + 86_400_000), createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

function fakeRepo(overrides: Partial<ISignupSessionRepo> = {}): ISignupSessionRepo {
  return {
    create: vi.fn(),
    findById: vi.fn(),
    findByEmailVerificationTokenHash: vi.fn(async () => baseSession()),
    findByResumeTokenHash: vi.fn(),
    existsActiveForEmail: vi.fn(),
    existsActiveForSubdomain: vi.fn(),
    updateState: vi.fn(async (_id, state, patch) => baseSession({ state, ...patch })),
    findExpiredInStates: vi.fn(),
    findInState: vi.fn(),
    ...overrides,
  };
}

describe("VerifyEmailService", () => {
  it("throws EmailVerificationTokenInvalidError when no session matches the token", async () => {
    const repo = fakeRepo({ findByEmailVerificationTokenHash: vi.fn(async () => null) });
    const service = new VerifyEmailService(repo);
    await expect(service.verifyEmail("bad-token")).rejects.toThrow(EmailVerificationTokenInvalidError);
  });

  it("throws EmailAlreadyVerifiedError when the session isn't in STARTED", async () => {
    const repo = fakeRepo({
      findByEmailVerificationTokenHash: vi.fn(async () => baseSession({ state: SignupState.EMAIL_VERIFIED, emailVerifiedAt: new Date() })),
    });
    const service = new VerifyEmailService(repo);
    await expect(service.verifyEmail("token")).rejects.toThrow(EmailAlreadyVerifiedError);
  });

  it("marks the session EMAIL_VERIFIED and returns nextStep PLAN_SELECTION", async () => {
    const repo = fakeRepo();
    const service = new VerifyEmailService(repo);

    const result = await service.verifyEmail("valid-token");

    expect(repo.updateState).toHaveBeenCalledWith("session-1", SignupState.EMAIL_VERIFIED, expect.objectContaining({ emailVerificationTokenHash: null }));
    expect(result).toEqual({ sessionId: "session-1", email: "founder@example.com", nextStep: "PLAN_SELECTION" });
  });
});
