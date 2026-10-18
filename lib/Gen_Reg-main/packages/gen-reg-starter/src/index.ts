// src/index.ts
export { createGenReg } from "./create-gen-reg.ts";
export type { GenRegConfig, GenRegModulesConfig, GenRegWorker, GenRegInstance } from "./create-gen-reg.ts";

export type {
  ISignupSessionRepo,
  SignupSessionRecord,
  CreateSignupSessionInput,
} from "./domain/ports/signup-session.repository.port.ts";
export type { EmailSender } from "./common/email-sender.ts";
export type {
  ITntClient,
  TenantResponse,
  ProvisioningJobResponse,
  CreateTenantInput,
} from "./domain/ports/tnt-client.port.ts";
export type { IAuthClient, RegisterResult } from "./domain/ports/auth-client.port.ts";
export type {
  IPaymentProvider,
  PaymentProviderKind,
  PaymentCheckoutParams,
  PaymentCheckoutResult,
  PaymentEventType,
  NormalizedPaymentWebhookEvent,
} from "./domain/ports/payment-provider.port.ts";
export type { IWebhookDedupStore } from "./domain/ports/webhook-dedup.port.ts";
export type { CaptchaVerifyResult, ICaptchaVerifier } from "./domain/ports/captcha-verifier.port.ts";

export { PrismaSignupSessionRepo } from "./modules/signup/v1/repo.ts";
export { ConsoleEmailSender } from "./common/email-sender.ts";
export { HttpTntClient } from "./infra/tnt-client/tnt-client.ts";
export { HttpAuthClient } from "./infra/auth-client/auth-client.ts";
export { StripePaymentProvider } from "./infra/payment/stripe-provider.ts";
export { RazorpayPaymentProvider } from "./infra/payment/razorpay-provider.ts";
export { ValkeyDedupStore } from "./infra/cache/valkey-dedup-store.ts";
export { TurnstileCaptchaVerifier } from "./infra/captcha/turnstile-verifier.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";
export { PLANS, findPlanByCode } from "./common/plans.ts";
export type { Plan } from "./common/plans.ts";

export { errorHandler } from "./middleware/error-handler.ts";
export {
  AppError,
  GenRegConfigError,
  SignupEmailAlreadyRegisteredError,
  SignupTenantSlugTakenError,
  SignupDomainBlockedError,
  EmailVerificationTokenInvalidError,
  EmailVerificationTokenExpiredError,
  EmailAlreadyVerifiedError,
  ResumeTokenNotFoundError,
  SignupSessionNotFoundError,
  GenAuthRegistrationError,
  GenTntProvisioningError,
  SignupStepOutOfOrderError,
  PlanNotFoundError,
  PaymentProviderError,
  PaymentProviderNotConfiguredError,
  WebhookSignatureInvalidError,
  CaptchaTokenMissingError,
  CaptchaVerificationFailedError,
} from "./common/errors.ts";
