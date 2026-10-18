// src/worker.test.ts
import { describe, it, expect, vi } from "vitest";
import { sweepProvisioning, sweepAbandoned } from "./worker.ts";
import { SignupState } from "./domain/enums/signup-state.enum.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "./domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "./domain/ports/tnt-client.port.ts";

function session(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "s1", email: "a@example.com", companyName: null, fullName: null, phone: null, source: null,
    referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null, desiredSubdomain: "acme",
    state: SignupState.PROVISIONING, emailVerificationTokenHash: null, emailVerifiedAt: new Date(),
    resumeTokenHash: null, authUserId: "u1", selectedPlanCode: null, selectedBillingCycle: null,
    paymentProvider: null, checkoutSessionId: null, paymentCustomerId: null,
    provisioningJobId: "job-1", provisionedTenantId: "tenant-1",
    lastProvisioningError: null, expiresAt: new Date(), createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

describe("sweepProvisioning", () => {
  it("moves a session to ACTIVE when Gen_TNT reports the tenant ACTIVE", async () => {
    const repo = {
      findInState: vi.fn(async () => [session()]),
      updateState: vi.fn(),
    } as unknown as ISignupSessionRepo;
    const tntClient = {
      getTenant: vi.fn(async () => ({ id: "tenant-1", status: "ACTIVE" })),
    } as unknown as ITntClient;

    await sweepProvisioning(repo, tntClient);

    expect(repo.updateState).toHaveBeenCalledWith("s1", SignupState.ACTIVE);
  });

  it("moves a session to PROVISION_FAILED when Gen_TNT's job is DEAD", async () => {
    const repo = {
      findInState: vi.fn(async () => [session()]),
      updateState: vi.fn(),
    } as unknown as ISignupSessionRepo;
    const tntClient = {
      getTenant: vi.fn(async () => ({ id: "tenant-1", status: "PROVISIONING" })),
      getJob: vi.fn(async () => ({ status: "DEAD", lastError: "step X failed permanently" })),
    } as unknown as ITntClient;

    await sweepProvisioning(repo, tntClient);

    expect(repo.updateState).toHaveBeenCalledWith("s1", SignupState.PROVISION_FAILED, {
      lastProvisioningError: "step X failed permanently",
    });
  });

  it("leaves the session untouched while still PROVISIONING and the job isn't DEAD", async () => {
    const repo = {
      findInState: vi.fn(async () => [session()]),
      updateState: vi.fn(),
    } as unknown as ISignupSessionRepo;
    const tntClient = {
      getTenant: vi.fn(async () => ({ id: "tenant-1", status: "PROVISIONING" })),
      getJob: vi.fn(async () => ({ status: "IN_PROGRESS", lastError: null })),
    } as unknown as ITntClient;

    await sweepProvisioning(repo, tntClient);

    expect(repo.updateState).not.toHaveBeenCalled();
  });
});

describe("sweepAbandoned", () => {
  it("marks expired STARTED/EMAIL_VERIFIED sessions ABANDONED", async () => {
    const repo = {
      findExpiredInStates: vi.fn(async () => [session({ state: SignupState.STARTED })]),
      updateState: vi.fn(),
    } as unknown as ISignupSessionRepo;

    await sweepAbandoned(repo);

    expect(repo.updateState).toHaveBeenCalledWith("s1", SignupState.ABANDONED);
  });
});
