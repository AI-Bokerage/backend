// src/common/errors.ts
export class AppError extends Error {
  constructor(
    public readonly statusCode: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = new.target.name;
  }
}

export class SignupEmailAlreadyRegisteredError extends AppError {
  constructor(email: string) {
    super(409, "SIGNUP_EMAIL_ALREADY_REGISTERED", `Email "${email}" is already registered`);
  }
}

export class SignupTenantSlugTakenError extends AppError {
  constructor(slug: string) {
    super(409, "SIGNUP_TENANT_SLUG_TAKEN", `Subdomain "${slug}" is already taken`);
  }
}

export class SignupDomainBlockedError extends AppError {
  constructor(domain: string) {
    super(400, "SIGNUP_DOMAIN_BLOCKED", `Email domain "${domain}" is not allowed`);
  }
}

export class EmailVerificationTokenInvalidError extends AppError {
  constructor() {
    super(400, "EMAIL_VERIFICATION_TOKEN_INVALID", "Verification token is invalid");
  }
}

export class EmailVerificationTokenExpiredError extends AppError {
  constructor() {
    super(400, "EMAIL_VERIFICATION_TOKEN_EXPIRED", "Verification token has expired");
  }
}

export class EmailAlreadyVerifiedError extends AppError {
  constructor() {
    super(409, "EMAIL_ALREADY_VERIFIED", "Email is already verified");
  }
}

export class ResumeTokenNotFoundError extends AppError {
  constructor() {
    super(404, "RESUME_TOKEN_NOT_FOUND", "Resume token not found or expired");
  }
}

export class SignupSessionNotFoundError extends AppError {
  constructor(id: string) {
    super(404, "SIGNUP_SESSION_NOT_FOUND", `Signup session "${id}" not found`);
  }
}

export class GenAuthRegistrationError extends AppError {
  constructor(message: string) {
    super(502, "GEN_AUTH_REGISTRATION_FAILED", message);
  }
}

export class GenTntProvisioningError extends AppError {
  constructor(message: string) {
    super(502, "GEN_TNT_PROVISIONING_FAILED", message);
  }
}

export class SignupStepOutOfOrderError extends AppError {
  constructor(expected: string, actual: string) {
    super(409, "SIGNUP_STEP_OUT_OF_ORDER", `Expected session state "${expected}" but found "${actual}"`);
  }
}

export class PlanNotFoundError extends AppError {
  constructor(planCode: string) {
    super(404, "PLAN_NOT_FOUND", `Plan "${planCode}" not found`);
  }
}

export class PaymentProviderError extends AppError {
  constructor(message: string) {
    super(502, "PAYMENT_PROVIDER_ERROR", message);
  }
}

export class PaymentProviderNotConfiguredError extends AppError {
  constructor(kind: string) {
    super(400, "PAYMENT_PROVIDER_NOT_CONFIGURED", `Payment provider "${kind}" is not configured`);
  }
}

export class WebhookSignatureInvalidError extends AppError {
  constructor() {
    super(400, "WEBHOOK_SIGNATURE_INVALID", "Webhook signature verification failed");
  }
}

export class CaptchaTokenMissingError extends AppError {
  constructor() {
    super(400, "CAPTCHA_TOKEN_MISSING", "CAPTCHA token is required");
  }
}

export class CaptchaVerificationFailedError extends AppError {
  constructor() {
    super(400, "CAPTCHA_VERIFICATION_FAILED", "CAPTCHA verification failed");
  }
}

// Boot-time configuration error — thrown by requireEnv() when createGenReg()
// needs an env var for a default adapter that was never supplied. Deliberately
// does NOT extend AppError: this is never thrown during request handling and
// never reaches errorHandler, so it has no HTTP status code.
export class GenRegConfigError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GenRegConfigError";
  }
}
