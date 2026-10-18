// src/create-gen-reg.test.ts
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import request from "supertest";
import { SignupState } from "./domain/enums/signup-state.enum.ts";
import { GenRegConfigError } from "./common/errors.ts";
import type { ISignupSessionRepo, SignupSessionRecord } from "./domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "./domain/ports/tnt-client.port.ts";
import type { IAuthClient } from "./domain/ports/auth-client.port.ts";
import type { EmailSender } from "./common/email-sender.ts";

function baseSession(overrides: Partial<SignupSessionRecord> = {}): SignupSessionRecord {
  return {
    id: "session-1", email: "founder@example.com", companyName: "Acme", fullName: null, phone: null,
    source: null, referralCode: null, utmSource: null, utmMedium: null, utmCampaign: null,
    desiredSubdomain: "acme", state: SignupState.STARTED,
    emailVerificationTokenHash: null, emailVerifiedAt: null, resumeTokenHash: null,
    authUserId: "auth-user-1", selectedPlanCode: null, selectedBillingCycle: null,
    paymentProvider: null, checkoutSessionId: null, paymentCustomerId: null,
    provisioningJobId: null, provisionedTenantId: null, lastProvisioningError: null,
    expiresAt: new Date(Date.now() + 86_400_000), createdAt: new Date(), updatedAt: new Date(),
    ...overrides,
  };
}

function fakeRepo(overrides: Partial<ISignupSessionRepo> = {}): ISignupSessionRepo {
  return {
    create: vi.fn(async () => baseSession()),
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
  return {
    isSlugTaken: vi.fn(async () => false),
    createTenant: vi.fn(async () => ({
      id: "tenant-1", slug: "acme", name: "Acme", status: "PROVISIONING", region: null,
      primaryOwnerUserId: "auth-user-1", provisioningJobId: "job-1", createdAt: "2026-01-01T00:00:00Z",
    })),
    getTenant: vi.fn(async () => ({ id: "tenant-1", status: "ACTIVE" }) as any),
    getJob: vi.fn(async () => ({ status: "IN_PROGRESS", lastError: null }) as any),
    ...overrides,
  };
}

function fakeAuthClient(overrides: Partial<IAuthClient> = {}): IAuthClient {
  return { register: vi.fn(async () => ({ userId: "auth-user-1" })), ...overrides };
}

function fakeEmailSender(): EmailSender {
  return { sendVerificationEmail: vi.fn(async () => undefined) };
}

function fakePaymentProvider(kind: "stripe" | "razorpay" = "stripe"): import("./domain/ports/payment-provider.port.ts").IPaymentProvider {
  return {
    provider: kind,
    createCheckoutSession: vi.fn(async () => ({ sessionId: "cs_1", sessionUrl: "https://pay", customerId: "cus_1" })),
    getWebhookSignatureHeader: vi.fn(() => "stripe-signature"),
    verifyAndNormalizeWebhook: vi.fn(() => ({
      id: "evt_1", type: "checkout.completed", sessionId: "cs_1", metadata: { session_id: "session-1" }, raw: {},
    })),
  };
}

function fakeDedupStore(): import("./domain/ports/webhook-dedup.port.ts").IWebhookDedupStore {
  return { tryAcquire: vi.fn(async () => true), release: vi.fn(async () => undefined) };
}

function fakeCaptchaVerifier(verified: boolean): import("./domain/ports/captcha-verifier.port.ts").ICaptchaVerifier {
  return { verify: vi.fn(async () => ({ verified })) };
}

describe("createGenReg", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  describe("default config", () => {
    beforeEach(() => {
      process.env.DATABASE_URL = "postgresql://unused/unused";
      process.env.GEN_TNT_BASE_URL = "http://localhost:8201";
      process.env.GEN_TNT_INTERNAL_SECRET = "test-secret";
      process.env.GEN_AUTH_BASE_URL = "http://localhost:8101";
      process.env.STRIPE_SECRET_KEY = "sk_test_unused";
      process.env.STRIPE_WEBHOOK_SECRET = "whsec_test_unused";
      process.env.VALKEY_URL = "redis://localhost:6382";
      process.env.TURNSTILE_SECRET_KEY = "test-turnstile-secret";
    });

    it("GET /health returns ok", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({});
      const res = await request(app).get("/health");
      expect(res.status).toBe(200);
      expect(res.body).toEqual({ status: "ok" });
    });

    it("GET /api/v1/signup/:sessionId returns 404 for a malformed id", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({ repo: fakeRepo() });
      const res = await request(app).get("/api/v1/signup/not-a-uuid");
      expect(res.status).toBe(404);
      expect(res.body.error).toBe("SIGNUP_SESSION_NOT_FOUND");
    });
  });

  describe("override path — proves pluggability actually wires through", () => {
    it("uses the supplied repo/emailSender/tntClient/authClient instead of building defaults", async () => {
      const repo = fakeRepo({
        existsActiveForSubdomain: vi.fn(async () => false),
        create: vi.fn(async () => baseSession({ id: "session-42" })),
      });
      const tntClient = fakeTntClient();
      const authClient = fakeAuthClient();
      const emailSender = fakeEmailSender();

      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({ repo, tntClient, authClient, emailSender, modules: { payment: false } });

      const res = await request(app).post("/api/v1/signup").send({
        email: "founder@example.com",
        password: "hunter2222",
        desiredSubdomain: "acme",
      });

      expect(res.status).toBe(201);
      expect(authClient.register).toHaveBeenCalledWith("founder@example.com", "hunter2222");
      expect(repo.create).toHaveBeenCalledOnce();
      expect(emailSender.sendVerificationEmail).toHaveBeenCalledOnce();
      // tntClient.isSlugTaken is called during signup validation — proves the
      // supplied fake, not a real HttpTntClient, was actually used.
      expect(tntClient.isSlugTaken).toHaveBeenCalledWith("acme");
    });

    it("uses the supplied paymentProviders/webhookDedupStore instead of building defaults", async () => {
      const repo = fakeRepo({
        findById: vi.fn(async () => baseSession({ state: SignupState.PAYMENT_PENDING })),
      });
      const stripe = fakePaymentProvider("stripe");
      const dedupStore = fakeDedupStore();

      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        paymentProviders: { stripe },
        webhookDedupStore: dedupStore,
      });

      await request(app)
        .post("/api/v1/signup/webhooks/stripe")
        .set("stripe-signature", "sig")
        .set("Content-Type", "application/json")
        .send(Buffer.from("{}"));

      expect(stripe.verifyAndNormalizeWebhook).toHaveBeenCalledOnce();
      expect(dedupStore.tryAcquire).toHaveBeenCalledWith("evt_1", expect.any(Number));
    });
  });

  describe("module toggles", () => {
    it("does not return a worker when modules.worker is false", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { worker } = createGenReg({
        repo: fakeRepo(),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { worker: false, payment: false },
      });
      expect(worker).toBeUndefined();
    });

    it("returns a worker with start/stop when modules.worker is true (default)", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { worker } = createGenReg({
        repo: fakeRepo(),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { payment: false },
      });
      expect(worker).toBeDefined();
      expect(typeof worker!.start).toBe("function");
      expect(typeof worker!.stop).toBe("function");
      worker!.start();
      worker!.stop();
    });

    it("does not mount signup routes when modules.signup is false", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({
        repo: fakeRepo(),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { signup: false, worker: false, payment: false },
      });
      const res = await request(app).post("/api/v1/signup").send({});
      expect(res.status).toBe(404);
    });

    it("does not mount verify-email routes when modules.verifyEmail is false", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const repo = fakeRepo();
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { verifyEmail: false, worker: false, payment: false },
      });
      const res = await request(app).get("/api/v1/signup/verify-email?token=abc");
      expect(res.status).toBe(404);
      // Proves the request never reached VerifyEmailService — a 404 alone can't
      // distinguish "genuinely unmounted" from "shadowed by the :sessionId route".
      expect(repo.findByEmailVerificationTokenHash).not.toHaveBeenCalled();
    });

    it("reaches the verify-email route (not shadowed by the :sessionId catch-all) when modules.verifyEmail is true (default)", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const repo = fakeRepo();
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { worker: false, payment: false },
      });
      await request(app).get("/api/v1/signup/verify-email?token=abc");
      expect(repo.findByEmailVerificationTokenHash).toHaveBeenCalled();
    });

    it("does not mount payment routes when modules.payment is false", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const repo = fakeRepo();
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { worker: false, payment: false },
      });
      const res = await request(app).post("/api/v1/signup/select-plan").send({ sessionId: "session-1", planCode: "STARTER" });
      expect(res.status).toBe(404);
    });

    it("mounts payment routes when modules.payment is true (default)", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const repo = fakeRepo({ findById: vi.fn(async () => baseSession({ state: SignupState.EMAIL_VERIFIED })) });
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        paymentProviders: { stripe: fakePaymentProvider("stripe") },
        webhookDedupStore: fakeDedupStore(),
        modules: { worker: false },
      });
      // ponytail: SelectPlanSchema requires sessionId to be a UUID — "session-1"
      // (used elsewhere in this file for repo-level fakes) fails that shape
      // check before the request ever reaches the mocked repo, so a real UUID
      // is used here specifically to exercise the route-mounted 200 path.
      const res = await request(app)
        .post("/api/v1/signup/select-plan")
        .send({ sessionId: "11111111-1111-1111-1111-111111111111", planCode: "STARTER" });
      expect(res.status).toBe(200);
    });
  });

  describe("captcha", () => {
    // ponytail: modules.captcha:true also mounts the dev-only token-gen page
    // (captchaDevRoutes) whenever NODE_ENV !== "production", which is the case
    // for this test run — so TURNSTILE_SITE_KEY must be set here even though
    // these tests only exercise the signup captcha guard, not the dev page.
    beforeEach(() => {
      process.env.TURNSTILE_SITE_KEY = "test-turnstile-site-key";
    });

    it("does not require a captchaToken when modules.captcha is false (default)", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({
        repo: fakeRepo({ existsActiveForSubdomain: vi.fn(async () => false) }),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        modules: { payment: false, worker: false },
      });

      const res = await request(app).post("/api/v1/signup").send({
        email: "founder@example.com",
        password: "hunter2222",
        desiredSubdomain: "acme",
      });

      expect(res.status).toBe(201);
    });

    it("rejects a signup with no captchaToken when modules.captcha is true", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const { app } = createGenReg({
        repo: fakeRepo(),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        captchaVerifier: fakeCaptchaVerifier(true),
        modules: { payment: false, worker: false, captcha: true },
      });

      const res = await request(app).post("/api/v1/signup").send({
        email: "founder@example.com",
        password: "hunter2222",
        desiredSubdomain: "acme",
      });

      expect(res.status).toBe(400);
      expect(res.body.error).toBe("CAPTCHA_TOKEN_MISSING");
    });

    it("uses the supplied captchaVerifier and blocks a signup it reports as not verified", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const captchaVerifier = fakeCaptchaVerifier(false);
      const { app } = createGenReg({
        repo: fakeRepo(),
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        captchaVerifier,
        modules: { payment: false, worker: false, captcha: true },
      });

      const res = await request(app).post("/api/v1/signup").send({
        email: "founder@example.com",
        password: "hunter2222",
        desiredSubdomain: "acme",
        captchaToken: "some-token",
      });

      expect(res.status).toBe(400);
      expect(res.body.error).toBe("CAPTCHA_VERIFICATION_FAILED");
      expect(captchaVerifier.verify).toHaveBeenCalledWith("some-token");
    });

    it("allows a signup through when the supplied captchaVerifier reports verified", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const repo = fakeRepo({ existsActiveForSubdomain: vi.fn(async () => false) });
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        captchaVerifier: fakeCaptchaVerifier(true),
        modules: { payment: false, worker: false, captcha: true },
      });

      const res = await request(app).post("/api/v1/signup").send({
        email: "founder@example.com",
        password: "hunter2222",
        desiredSubdomain: "acme",
        captchaToken: "some-token",
      });

      expect(res.status).toBe(201);
    });

    it("does not guard check-subdomain, resume, select-plan, or checkout even when modules.captcha is true", async () => {
      const { createGenReg } = await import("./create-gen-reg.ts");
      const repo = fakeRepo({ findById: vi.fn(async () => baseSession({ state: SignupState.EMAIL_VERIFIED })) });
      const { app } = createGenReg({
        repo,
        tntClient: fakeTntClient(),
        authClient: fakeAuthClient(),
        emailSender: fakeEmailSender(),
        captchaVerifier: fakeCaptchaVerifier(true),
        paymentProviders: { stripe: fakePaymentProvider("stripe") },
        webhookDedupStore: fakeDedupStore(),
        modules: { worker: false, captcha: true },
      });

      const checkSubdomain = await request(app).get("/api/v1/signup/check-subdomain?value=someslug");
      const resume = await request(app).get("/api/v1/signup/resume?token=some-resume-token");
      const selectPlan = await request(app)
        .post("/api/v1/signup/select-plan")
        .send({ sessionId: "11111111-1111-1111-1111-111111111111", planCode: "STARTER" });
      const checkout = await request(app)
        .post("/api/v1/signup/checkout")
        .send({
          sessionId: "11111111-1111-1111-1111-111111111111",
          successUrl: "https://example.com/success",
          cancelUrl: "https://example.com/cancel",
        });

      // None of these send a captchaToken. A 400 with CAPTCHA_TOKEN_MISSING would mean
      // the captcha middleware incorrectly guarded this route; anything else (200, 404,
      // 409 SIGNUP_STEP_OUT_OF_ORDER, etc.) proves it wasn't guarded, which is correct —
      // only POST /signup should ever be gated by captcha.
      expect(checkSubdomain.status).not.toBe(400);
      expect(resume.status).not.toBe(400);
      expect(selectPlan.status).not.toBe(400);
      expect(checkout.status).not.toBe(400);
    });
  });

  describe("lazy env validation", () => {
    const ORIGINAL_ENV = { ...process.env };

    afterEach(() => {
      process.env = { ...ORIGINAL_ENV };
    });

    it("does not require GEN_TNT_BASE_URL when signup/verifyEmail/worker are all disabled", async () => {
      delete process.env.GEN_TNT_BASE_URL;
      delete process.env.GEN_TNT_INTERNAL_SECRET;
      delete process.env.GEN_AUTH_BASE_URL;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          modules: { signup: false, verifyEmail: false, worker: false, payment: false },
        }),
      ).not.toThrow();
    });

    it("throws GenRegConfigError when signup is enabled, no tntClient override, and GEN_TNT_BASE_URL is missing", async () => {
      delete process.env.GEN_TNT_BASE_URL;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          authClient: fakeAuthClient(),
          modules: { signup: true, verifyEmail: false, worker: false, payment: false },
        }),
      ).toThrow(GenRegConfigError);
    });

    it("does not require STRIPE_SECRET_KEY/VALKEY_URL when modules.payment is false", async () => {
      delete process.env.STRIPE_SECRET_KEY;
      delete process.env.STRIPE_WEBHOOK_SECRET;
      delete process.env.VALKEY_URL;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          modules: { signup: false, verifyEmail: false, payment: false, worker: false },
        }),
      ).not.toThrow();
    });

    it("throws GenRegConfigError when payment is enabled, no webhookDedupStore override, and VALKEY_URL is missing", async () => {
      delete process.env.VALKEY_URL;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          tntClient: fakeTntClient(),
          paymentProviders: { stripe: fakePaymentProvider("stripe") },
          modules: { signup: false, verifyEmail: false, payment: true, worker: false },
        }),
      ).toThrow(GenRegConfigError);
    });

    it("throws GenRegConfigError when payment is enabled, no paymentProviders override, and STRIPE_SECRET_KEY is missing (stripe is the default provider)", async () => {
      delete process.env.STRIPE_SECRET_KEY;
      delete process.env.STRIPE_WEBHOOK_SECRET;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          tntClient: fakeTntClient(),
          webhookDedupStore: fakeDedupStore(),
          modules: { signup: false, verifyEmail: false, payment: true, worker: false },
        }),
      ).toThrow(GenRegConfigError);
    });

    it("does not require TURNSTILE_SECRET_KEY when modules.captcha is false (default)", async () => {
      delete process.env.TURNSTILE_SECRET_KEY;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          modules: { signup: false, verifyEmail: false, payment: false, worker: false },
        }),
      ).not.toThrow();
    });

    it("throws GenRegConfigError when captcha is enabled, no captchaVerifier override, and TURNSTILE_SECRET_KEY is missing", async () => {
      delete process.env.TURNSTILE_SECRET_KEY;
      const { createGenReg } = await import("./create-gen-reg.ts");
      expect(() =>
        createGenReg({
          repo: fakeRepo(),
          tntClient: fakeTntClient(),
          authClient: fakeAuthClient(),
          modules: { signup: true, verifyEmail: false, payment: false, worker: false, captcha: true },
        }),
      ).toThrow(GenRegConfigError);
    });
  });
});
