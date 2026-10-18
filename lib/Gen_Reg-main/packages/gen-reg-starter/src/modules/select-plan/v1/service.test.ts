import { describe, it, expect, vi } from "vitest";
import { SelectPlanService } from "./service.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import { SignupSessionNotFoundError, SignupStepOutOfOrderError, PlanNotFoundError } from "../../../common/errors.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "../../../domain/ports/signup-session.repository.port.ts";

function baseSession(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "session-1", email: "founder@example.com", companyName: "Acme", fullName: null, phone: null,
    source: null, referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null,
    desiredSubdomain: "acme", state: SignupState.EMAIL_VERIFIED,
    emailVerificationTokenHash: null, emailVerifiedAt: new Date(), resumeTokenHash: null,
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
    findById: vi.fn(async () => baseSession()),
    findByEmailVerificationTokenHash: vi.fn(),
    findByResumeTokenHash: vi.fn(),
    existsActiveForEmail: vi.fn(),
    existsActiveForSubdomain: vi.fn(),
    updateState: vi.fn(async (_id, state, patch) => baseSession({ state, ...patch })),
    findExpiredInStates: vi.fn(),
    findInState: vi.fn(),
    ...overrides,
  };
}

describe("SelectPlanService", () => {
  it("throws SignupSessionNotFoundError when the session doesn't exist", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => null) });
    const service = new SelectPlanService(repo);
    await expect(
      service.selectPlan({ sessionId: "missing", planCode: "STARTER", billingCycle: "MONTHLY" }),
    ).rejects.toThrow(SignupSessionNotFoundError);
  });

  it("throws SignupStepOutOfOrderError when the session isn't EMAIL_VERIFIED", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => baseSession({ state: SignupState.STARTED })) });
    const service = new SelectPlanService(repo);
    await expect(
      service.selectPlan({ sessionId: "session-1", planCode: "STARTER", billingCycle: "MONTHLY" }),
    ).rejects.toThrow(SignupStepOutOfOrderError);
  });

  it("throws PlanNotFoundError for an unknown plan code", async () => {
    const repo = fakeRepo();
    const service = new SelectPlanService(repo);
    await expect(
      service.selectPlan({ sessionId: "session-1", planCode: "NOT_A_PLAN", billingCycle: "MONTHLY" }),
    ).rejects.toThrow(PlanNotFoundError);
  });

  it("transitions to PLAN_SELECTED and persists the plan code/billing cycle", async () => {
    const repo = fakeRepo();
    const service = new SelectPlanService(repo);

    const result = await service.selectPlan({ sessionId: "session-1", planCode: "STARTER", billingCycle: "ANNUAL" });

    expect(repo.updateState).toHaveBeenCalledWith("session-1", SignupState.PLAN_SELECTED, {
      selectedPlanCode: "STARTER",
      selectedBillingCycle: "ANNUAL",
    });
    expect(result).toEqual({
      sessionId: "session-1",
      planCode: "STARTER",
      billingCycle: "ANNUAL",
      nextStep: "CHECKOUT",
    });
  });
});
