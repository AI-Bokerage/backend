// src/modules/webhooks/v1/service.test.ts
import { describe, it, expect, vi } from "vitest";
import { WebhooksService } from "./service.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import { WebhookSignatureInvalidError } from "../../../common/errors.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "../../../domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "../../../domain/ports/tnt-client.port.ts";
import type { IPaymentProvider, NormalizedPaymentWebhookEvent } from "../../../domain/ports/payment-provider.port.ts";
import type { IWebhookDedupStore } from "../../../domain/ports/webhook-dedup.port.ts";

function baseSession(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "session-1", email: "founder@example.com", companyName: "Acme", fullName: null, phone: null,
    source: null, referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null,
    desiredSubdomain: "acme", state: SignupState.PAYMENT_PENDING,
    emailVerificationTokenHash: null, emailVerifiedAt: new Date(), resumeTokenHash: null,
    authUserId: "auth-user-1", selectedPlanCode: "STARTER", selectedBillingCycle: "MONTHLY",
    paymentProvider: "stripe", checkoutSessionId: "cs_1", paymentCustomerId: null,
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

function fakeTntClient(overrides: Partial<ITntClient> = {}): ITntClient {
  return {
    isSlugTaken: vi.fn(async () => false),
    createTenant: vi.fn(async () => ({
      id: "tenant-1", slug: "acme", name: "Acme", status: "PROVISIONING", region: null,
      primaryOwnerUserId: "auth-user-1", provisioningJobId: "job-1", createdAt: "2026-01-01T00:00:00Z",
    })),
    getTenant: vi.fn(),
    getJob: vi.fn(),
    ...overrides,
  };
}

function fakeProvider(event: NormalizedPaymentWebhookEvent, overrides: Partial<IPaymentProvider> = {}): IPaymentProvider {
  return {
    provider: "stripe",
    createCheckoutSession: vi.fn(),
    getWebhookSignatureHeader: vi.fn(() => "stripe-signature"),
    verifyAndNormalizeWebhook: vi.fn(() => event),
    ...overrides,
  };
}

function fakeDedupStore(overrides: Partial<IWebhookDedupStore> = {}): IWebhookDedupStore {
  return {
    tryAcquire: vi.fn(async () => true),
    release: vi.fn(async () => undefined),
    ...overrides,
  };
}

describe("WebhooksService", () => {
  it("throws WebhookSignatureInvalidError when the provider rejects the signature", () => {
    const provider = fakeProvider({} as NormalizedPaymentWebhookEvent, {
      verifyAndNormalizeWebhook: vi.fn(() => { throw new WebhookSignatureInvalidError(); }),
    });
    const service = new WebhooksService(fakeRepo(), fakeTntClient(), { stripe: provider }, fakeDedupStore());
    expect(() => service.verifyAndNormalize("stripe", Buffer.from("{}"), "bad-sig")).toThrow(WebhookSignatureInvalidError);
  });

  it("skips processing (no-op) when the dedup store reports a duplicate", async () => {
    const repo = fakeRepo();
    const dedupStore = fakeDedupStore({ tryAcquire: vi.fn(async () => false) });
    const service = new WebhooksService(repo, fakeTntClient(), {}, dedupStore);

    await service.handleEvent({
      id: "evt-1", type: "checkout.completed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    });

    expect(repo.updateState).not.toHaveBeenCalled();
  });

  it("checkout.completed: transitions PAYMENT_PENDING -> PAYMENT_SUCCEEDED -> PROVISIONING and calls Gen_TNT", async () => {
    const repo = fakeRepo();
    const tntClient = fakeTntClient();
    const dedupStore = fakeDedupStore();
    const service = new WebhooksService(repo, tntClient, {}, dedupStore);

    await service.handleEvent({
      id: "evt-1", type: "checkout.completed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    });

    expect(repo.updateState).toHaveBeenNthCalledWith(1, "session-1", SignupState.PAYMENT_SUCCEEDED, {});
    expect(tntClient.createTenant).toHaveBeenCalledWith({
      name: "Acme", slug: "acme", primaryOwnerUserId: "auth-user-1", idempotencyKey: "session-1",
    });
    expect(repo.updateState).toHaveBeenNthCalledWith(2, "session-1", SignupState.PROVISIONING, {
      provisioningJobId: "job-1", provisionedTenantId: "tenant-1",
    });
  });

  it("checkout.completed is a no-op when the session is already past PAYMENT_PENDING", async () => {
    const repo = fakeRepo({ findById: vi.fn(async () => baseSession({ state: SignupState.PROVISIONING })) });
    const tntClient = fakeTntClient();
    const service = new WebhooksService(repo, tntClient, {}, fakeDedupStore());

    await service.handleEvent({
      id: "evt-1", type: "checkout.completed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    });

    expect(tntClient.createTenant).not.toHaveBeenCalled();
  });

  it("checkout.expired reverts PAYMENT_PENDING -> PLAN_SELECTED", async () => {
    const repo = fakeRepo();
    const service = new WebhooksService(repo, fakeTntClient(), {}, fakeDedupStore());

    await service.handleEvent({
      id: "evt-2", type: "checkout.expired", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    });

    expect(repo.updateState).toHaveBeenCalledWith("session-1", SignupState.PLAN_SELECTED);
  });

  it("checkout.failed reverts PAYMENT_PENDING -> PLAN_SELECTED", async () => {
    const repo = fakeRepo();
    const service = new WebhooksService(repo, fakeTntClient(), {}, fakeDedupStore());

    await service.handleEvent({
      id: "evt-3", type: "checkout.failed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    });

    expect(repo.updateState).toHaveBeenCalledWith("session-1", SignupState.PLAN_SELECTED);
  });

  it("releases the dedup key and rethrows when processing throws", async () => {
    const repo = fakeRepo({
      updateState: vi.fn(async () => { throw new Error("db down"); }),
    });
    const dedupStore = fakeDedupStore();
    const service = new WebhooksService(repo, fakeTntClient(), {}, dedupStore);

    await expect(
      service.handleEvent({ id: "evt-4", type: "checkout.completed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {} }),
    ).rejects.toThrow("db down");
    expect(dedupStore.release).toHaveBeenCalledWith("evt-4");
  });
});
