// src/create-gen-reg.ts
import express, { type Express } from "express";
import "express-async-errors";
import helmet from "helmet";
import cors from "cors";
import { z } from "zod";
import { env, requireEnv } from "./config/env.ts";
import { logger } from "./common/logger.ts";
import { getPrismaClient } from "./infra/persistence/prisma-client.ts";
import { PrismaSignupSessionRepo } from "./modules/signup/v1/repo.ts";
import { HttpTntClient } from "./infra/tnt-client/tnt-client.ts";
import { HttpAuthClient } from "./infra/auth-client/auth-client.ts";
import { ConsoleEmailSender } from "./common/email-sender.ts";
import { StripePaymentProvider } from "./infra/payment/stripe-provider.ts";
import { RazorpayPaymentProvider } from "./infra/payment/razorpay-provider.ts";
import { ValkeyDedupStore } from "./infra/cache/valkey-dedup-store.ts";
import { TurnstileCaptchaVerifier } from "./infra/captcha/turnstile-verifier.ts";
import { createCaptchaMiddleware } from "./middleware/captcha-verify.ts";
import { captchaDevRoutes } from "./modules/captcha/v1/routes.ts";
import { SignupService } from "./modules/signup/v1/service.ts";
import { SignupController } from "./modules/signup/v1/controller.ts";
import { signupRoutes } from "./modules/signup/v1/routes.ts";
import { VerifyEmailService } from "./modules/verify-email/v1/service.ts";
import { VerifyEmailController } from "./modules/verify-email/v1/controller.ts";
import { verifyEmailRoutes } from "./modules/verify-email/v1/routes.ts";
import { SelectPlanService } from "./modules/select-plan/v1/service.ts";
import { SelectPlanController } from "./modules/select-plan/v1/controller.ts";
import { selectPlanRoutes } from "./modules/select-plan/v1/routes.ts";
import { CheckoutService } from "./modules/checkout/v1/service.ts";
import { CheckoutController } from "./modules/checkout/v1/controller.ts";
import { checkoutRoutes } from "./modules/checkout/v1/routes.ts";
import { WebhooksService } from "./modules/webhooks/v1/service.ts";
import { WebhooksController } from "./modules/webhooks/v1/controller.ts";
import { webhooksRoutes } from "./modules/webhooks/v1/routes.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { SignupSessionNotFoundError } from "./common/errors.ts";
import { sweepProvisioning, sweepAbandoned } from "./worker.ts";
import type { ISignupSessionRepo } from "./domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "./domain/ports/tnt-client.port.ts";
import type { IAuthClient } from "./domain/ports/auth-client.port.ts";
import type { EmailSender } from "./common/email-sender.ts";
import type { IPaymentProvider, PaymentProviderKind } from "./domain/ports/payment-provider.port.ts";
import type { IWebhookDedupStore } from "./domain/ports/webhook-dedup.port.ts";
import type { ICaptchaVerifier } from "./domain/ports/captcha-verifier.port.ts";

const SessionIdParamSchema = z.string().uuid();

export interface GenRegModulesConfig {
  signup?: boolean;
  verifyEmail?: boolean;
  payment?: boolean;
  captcha?: boolean;
  worker?: boolean;
}

export interface GenRegConfig {
  repo?: ISignupSessionRepo;
  emailSender?: EmailSender;
  tntClient?: ITntClient;
  authClient?: IAuthClient;
  paymentProviders?: Partial<Record<PaymentProviderKind, IPaymentProvider>>;
  defaultPaymentProvider?: PaymentProviderKind;
  webhookDedupStore?: IWebhookDedupStore;
  captchaVerifier?: ICaptchaVerifier;
  modules?: GenRegModulesConfig;
}

export interface GenRegWorker {
  start(): void;
  stop(): void;
}

export interface GenRegInstance {
  app: Express;
  worker?: GenRegWorker;
}

function resolveRepo(override: ISignupSessionRepo | undefined): ISignupSessionRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaSignupSessionRepo(getPrismaClient());
}

function resolveTntClient(override: ITntClient | undefined): ITntClient {
  if (override) return override;
  const baseUrl = requireEnv("GEN_TNT_BASE_URL");
  const secret = requireEnv("GEN_TNT_INTERNAL_SECRET");
  return new HttpTntClient(baseUrl, secret);
}

function resolveAuthClient(override: IAuthClient | undefined): IAuthClient {
  if (override) return override;
  const baseUrl = requireEnv("GEN_AUTH_BASE_URL");
  return new HttpAuthClient(baseUrl);
}

function resolveEmailSender(override: EmailSender | undefined): EmailSender {
  return override ?? new ConsoleEmailSender();
}

// `isDefault` controls fail-fast behavior: the provider that `defaultPaymentProvider`
// points at MUST be configured (missing env throws GenRegConfigError at boot,
// same as every other required-by-default adapter in this file). The OTHER
// provider is genuinely optional — a deployment using only Stripe should never
// be forced to configure Razorpay — so it silently resolves to `undefined`
// when none of its env vars are set, and only throws on a *partial* config
// (some but not all of its vars set), which is a real misconfiguration worth
// surfacing rather than silently ignoring.
function resolveStripeProvider(override: IPaymentProvider | undefined, isDefault: boolean): IPaymentProvider | undefined {
  if (override) return override;
  if (!isDefault && !process.env.STRIPE_SECRET_KEY && !process.env.STRIPE_WEBHOOK_SECRET) return undefined;
  return new StripePaymentProvider(requireEnv("STRIPE_SECRET_KEY"), requireEnv("STRIPE_WEBHOOK_SECRET"));
}

function resolveRazorpayProvider(override: IPaymentProvider | undefined, isDefault: boolean): IPaymentProvider | undefined {
  if (override) return override;
  if (!isDefault && !process.env.RAZORPAY_KEY_ID && !process.env.RAZORPAY_KEY_SECRET && !process.env.RAZORPAY_WEBHOOK_SECRET) {
    return undefined;
  }
  return new RazorpayPaymentProvider(
    requireEnv("RAZORPAY_KEY_ID"),
    requireEnv("RAZORPAY_KEY_SECRET"),
    requireEnv("RAZORPAY_WEBHOOK_SECRET"),
  );
}

function resolvePaymentProviders(
  override: Partial<Record<PaymentProviderKind, IPaymentProvider>> | undefined,
  defaultProvider: PaymentProviderKind,
): Partial<Record<PaymentProviderKind, IPaymentProvider>> {
  return {
    stripe: resolveStripeProvider(override?.stripe, defaultProvider === "stripe"),
    razorpay: resolveRazorpayProvider(override?.razorpay, defaultProvider === "razorpay"),
  };
}

function resolveWebhookDedupStore(override: IWebhookDedupStore | undefined): IWebhookDedupStore {
  if (override) return override;
  const redisUrl = requireEnv("VALKEY_URL");
  return new ValkeyDedupStore(redisUrl);
}

function resolveCaptchaVerifier(override: ICaptchaVerifier | undefined): ICaptchaVerifier {
  if (override) return override;
  const secretKey = requireEnv("TURNSTILE_SECRET_KEY");
  return new TurnstileCaptchaVerifier(secretKey, env.TURNSTILE_VERIFY_URL);
}

function buildWorker(repo: ISignupSessionRepo, tntClient: ITntClient): GenRegWorker {
  let provisioningInterval: ReturnType<typeof setInterval> | undefined;
  let abandonInterval: ReturnType<typeof setInterval> | undefined;

  return {
    start() {
      provisioningInterval = setInterval(() => {
        sweepProvisioning(repo, tntClient).catch((err) => logger.error({ err }, "sweepProvisioning failed"));
      }, env.PROVISIONING_POLL_INTERVAL_MS);

      abandonInterval = setInterval(() => {
        sweepAbandoned(repo).catch((err) => logger.error({ err }, "sweepAbandoned failed"));
      }, env.ABANDON_SWEEP_INTERVAL_MS);
    },
    stop() {
      if (provisioningInterval) clearInterval(provisioningInterval);
      if (abandonInterval) clearInterval(abandonInterval);
    },
  };
}

export function createGenReg(config: GenRegConfig): GenRegInstance {
  const modules: Required<GenRegModulesConfig> = {
    signup: config.modules?.signup ?? true,
    verifyEmail: config.modules?.verifyEmail ?? true,
    payment: config.modules?.payment ?? true,
    captcha: config.modules?.captcha ?? false,
    worker: config.modules?.worker ?? true,
  };

  const repo = resolveRepo(config.repo);
  const emailSender = resolveEmailSender(config.emailSender);

  const needsTntClient = modules.signup || modules.payment || modules.worker;
  const tntClient = needsTntClient ? resolveTntClient(config.tntClient) : undefined;

  const app = express();
  app.use(helmet());
  app.use(cors());

  if (modules.payment) {
    // Raw body needed for payment-provider webhook signature verification —
    // must be registered before the app-wide express.json() below, and only
    // for this specific path, or signature verification would see re-serialized
    // JSON instead of the exact bytes the provider signed.
    app.use("/api/v1/signup/webhooks", express.raw({ type: "application/json" }));
  }
  app.use(express.json());

  app.get("/health", (_req, res) => res.json({ status: "ok" }));

  if (modules.signup) {
    const authClient = resolveAuthClient(config.authClient);
    const signupService = new SignupService(repo, tntClient!, authClient, emailSender);
    const captchaMiddleware = modules.captcha
      ? createCaptchaMiddleware(resolveCaptchaVerifier(config.captchaVerifier))
      : undefined;
    app.use("/api/v1", signupRoutes(new SignupController(signupService), captchaMiddleware));
  }

  if (modules.captcha && env.NODE_ENV !== "production") {
    const siteKey = requireEnv("TURNSTILE_SITE_KEY");
    app.use("/api/v1", captchaDevRoutes(siteKey, env.TURNSTILE_CDN_URL));
  }

  if (modules.verifyEmail) {
    const verifyEmailService = new VerifyEmailService(repo);
    app.use("/api/v1", verifyEmailRoutes(new VerifyEmailController(verifyEmailService)));
  }

  let paymentProviders: Partial<Record<PaymentProviderKind, IPaymentProvider>> = {};
  if (modules.payment) {
    const defaultProvider = config.defaultPaymentProvider ?? "stripe";
    paymentProviders = resolvePaymentProviders(config.paymentProviders, defaultProvider);
    const dedupStore = resolveWebhookDedupStore(config.webhookDedupStore);

    const selectPlanService = new SelectPlanService(repo);
    app.use("/api/v1", selectPlanRoutes(new SelectPlanController(selectPlanService)));

    const checkoutService = new CheckoutService(repo, paymentProviders, defaultProvider);
    app.use("/api/v1", checkoutRoutes(new CheckoutController(checkoutService)));

    const webhooksService = new WebhooksService(repo, tntClient!, paymentProviders, dedupStore);
    app.use("/api/v1", webhooksRoutes(new WebhooksController(webhooksService)));
  }

  // Registered after verifyEmailRoutes/select-plan/checkout/webhooks so the
  // :sessionId wildcard doesn't shadow any literal route above (all of those
  // are POST-only or a distinct GET path, but this ordering keeps the same
  // discipline that fixed the original signup/verifyEmail shadowing bug).
  if (modules.signup) {
    app.get("/api/v1/signup/:sessionId", async (req, res) => {
      const parsed = SessionIdParamSchema.safeParse(req.params.sessionId);
      if (!parsed.success) throw new SignupSessionNotFoundError(req.params.sessionId);
      const session = await repo.findById(parsed.data);
      if (!session) throw new SignupSessionNotFoundError(req.params.sessionId);
      res.json({
        sessionId: session.id,
        state: session.state,
        provisionedTenantId: session.provisionedTenantId,
        lastProvisioningError: session.lastProvisioningError,
      });
    });
  }

  app.use(errorHandler);

  const worker = modules.worker ? buildWorker(repo, tntClient!) : undefined;

  return { app, worker };
}
