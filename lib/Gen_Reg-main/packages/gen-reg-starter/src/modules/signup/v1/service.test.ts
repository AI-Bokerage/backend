// src/modules/signup/v1/service.test.ts
import { describe, it, expect, vi, beforeEach } from "vitest";
import { SignupService } from "./service.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import {
  SignupDomainBlockedError,
  SignupEmailAlreadyRegisteredError,
  SignupTenantSlugTakenError,
} from "../../../common/errors.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "../../../domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "../../../domain/ports/tnt-client.port.ts";
import type { IAuthClient } from "../../../domain/ports/auth-client.port.ts";
import type { EmailSender } from "../../../common/email-sender.ts";

function fakeRepo(overrides: Partial<ISignupSessionRepo> = {}): ISignupSessionRepo {
  return {
    create: vi.fn(async (input) => ({
      id: "session-1",
      state: SignupState.STARTED,
      companyName: null, fullName: null, phone: null, source: null, referralCode: null,
      utmSource: null, utmMedium: null, utmCampaign: null,
      emailVerificationTokenHash: null, emailVerifiedAt: null, resumeTokenHash: null,
      selectedPlanCode: null, selectedBillingCycle: null, paymentProvider: null,
      checkoutSessionId: null, paymentCustomerId: null,
      provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
      createdAt: new Date(), updatedAt: new Date(),
      ...input,
    } as SignupSessionRecord)),
    findById: vi.fn(async () => null),
    findByEmailVerificationTokenHash: vi.fn(async () => null),
    findByResumeTokenHash: vi.fn(async () => null),
    existsActiveForEmail: vi.fn(async () => false),
    existsActiveForSubdomain: vi.fn(async () => false),
    updateState: vi.fn(),
    findExpiredInStates: vi.fn(async () => []),
    findInState: vi.fn(async () => []),
    ...overrides,
  };
}

function fakeTntClient(overrides: Partial<ITntClient> = {}): ITntClient {
  return { isSlugTaken: vi.fn(async () => false), ...overrides } as unknown as ITntClient;
}

function fakeAuthClient(overrides: Partial<IAuthClient> = {}): IAuthClient {
  return { register: vi.fn(async () => ({ userId: "auth-user-1" })), ...overrides } as unknown as IAuthClient;
}

function fakeEmailSender(): EmailSender {
  return { sendVerificationEmail: vi.fn(async () => undefined) };
}

describe("SignupService", () => {
  const baseInput = {
    email: "founder@example.com",
    password: "hunter2222",
    desiredSubdomain: "acme",
    source: "web" as const,
  };

  it("rejects a disposable email domain before touching the repo", async () => {
    const repo = fakeRepo();
    const service = new SignupService(repo, fakeTntClient(), fakeAuthClient(), fakeEmailSender());

    await expect(service.startSignup({ ...baseInput, email: "x@mailinator.com" })).rejects.toThrow(SignupDomainBlockedError);
    expect(repo.create).not.toHaveBeenCalled();
  });

  it("rejects a reserved subdomain", async () => {
    const service = new SignupService(fakeRepo(), fakeTntClient(), fakeAuthClient(), fakeEmailSender());
    await expect(service.startSignup({ ...baseInput, desiredSubdomain: "www" })).rejects.toThrow(SignupTenantSlugTakenError);
  });

  it("rejects when the email already has an active local session", async () => {
    const repo = fakeRepo({ existsActiveForEmail: vi.fn(async () => true) });
    const service = new SignupService(repo, fakeTntClient(), fakeAuthClient(), fakeEmailSender());
    await expect(service.startSignup(baseInput)).rejects.toThrow(SignupEmailAlreadyRegisteredError);
  });

  it("rejects when the subdomain is taken locally", async () => {
    const repo = fakeRepo({ existsActiveForSubdomain: vi.fn(async () => true) });
    const service = new SignupService(repo, fakeTntClient(), fakeAuthClient(), fakeEmailSender());
    await expect(service.startSignup(baseInput)).rejects.toThrow(SignupTenantSlugTakenError);
  });

  it("rejects when Gen_TNT reports the slug already taken", async () => {
    const service = new SignupService(
      fakeRepo(), fakeTntClient({ isSlugTaken: vi.fn(async () => true) }), fakeAuthClient(), fakeEmailSender(),
    );
    await expect(service.startSignup(baseInput)).rejects.toThrow(SignupTenantSlugTakenError);
  });

  it("registers with Gen_Auth, creates the session, and sends a verification email on success", async () => {
    const repo = fakeRepo();
    const authClient = fakeAuthClient();
    const emailSender = fakeEmailSender();
    const service = new SignupService(repo, fakeTntClient(), authClient, emailSender);

    const result = await service.startSignup(baseInput);

    expect(authClient.register).toHaveBeenCalledWith("founder@example.com", "hunter2222");
    expect(repo.create).toHaveBeenCalledWith(expect.objectContaining({ authUserId: "auth-user-1", email: "founder@example.com" }));
    expect(emailSender.sendVerificationEmail).toHaveBeenCalledOnce();
    expect(result.sessionId).toBe("session-1");
  });

  it("propagates a Gen_Auth registration failure without creating a session", async () => {
    const repo = fakeRepo();
    const authClient = fakeAuthClient({ register: vi.fn(async () => { throw new SignupEmailAlreadyRegisteredError("founder@example.com"); }) });
    const service = new SignupService(repo, fakeTntClient(), authClient, fakeEmailSender());

    await expect(service.startSignup(baseInput)).rejects.toThrow(SignupEmailAlreadyRegisteredError);
    expect(repo.create).not.toHaveBeenCalled();
  });

  describe("checkSubdomainAvailability", () => {
    it("returns invalid for a malformed slug without querying anything", async () => {
      const repo = fakeRepo();
      const tntClient = fakeTntClient();
      const service = new SignupService(repo, tntClient, fakeAuthClient(), fakeEmailSender());

      const result = await service.checkSubdomainAvailability("ab");
      expect(result).toEqual({ available: false, normalized: "ab", valid: false });
      expect(tntClient.isSlugTaken).not.toHaveBeenCalled();
    });

    it("returns available:false for a reserved subdomain", async () => {
      const service = new SignupService(fakeRepo(), fakeTntClient(), fakeAuthClient(), fakeEmailSender());
      const result = await service.checkSubdomainAvailability("www");
      expect(result).toEqual({ available: false, normalized: "www", valid: true });
    });

    it("checks Gen_TNT when locally free", async () => {
      const service = new SignupService(fakeRepo(), fakeTntClient({ isSlugTaken: vi.fn(async () => true) }), fakeAuthClient(), fakeEmailSender());
      const result = await service.checkSubdomainAvailability("acme");
      expect(result).toEqual({ available: false, normalized: "acme", valid: true });
    });
  });

  describe("resumeSignup", () => {
    it("throws ResumeTokenNotFoundError when no session matches the hash", async () => {
      const service = new SignupService(fakeRepo(), fakeTntClient(), fakeAuthClient(), fakeEmailSender());
      await expect(service.resumeSignup("bad-token")).rejects.toThrow();
    });
  });
});
