// src/modules/signup/v1/service.ts
import type { ISignupSessionRepo, SignupSessionRecord } from "../../../domain/ports/signup-session.repository.port.ts";
import type { ITntClient } from "../../../domain/ports/tnt-client.port.ts";
import type { IAuthClient } from "../../../domain/ports/auth-client.port.ts";
import type { EmailSender } from "../../../common/email-sender.ts";
import type { StartSignupInput } from "./schema.ts";
import { toSlug, isValidSlug, RESERVED_SUBDOMAINS } from "../../../common/slug.ts";
import { generateToken, hashToken } from "../../../common/token.ts";
import { DISPOSABLE_DOMAINS } from "../../../config/disposable-domains.ts";
import { env } from "../../../config/env.ts";
import {
  SignupDomainBlockedError,
  SignupEmailAlreadyRegisteredError,
  SignupTenantSlugTakenError,
  ResumeTokenNotFoundError,
} from "../../../common/errors.ts";

export interface SubdomainCheckResult {
  available: boolean;
  normalized: string;
  valid: boolean;
}

export interface ResumeSignupResult {
  sessionId: string;
  email: string;
  state: SignupSessionRecord["state"];
  companyName: string | null;
  desiredSubdomain: string;
}

export class SignupService {
  constructor(
    private readonly repo: ISignupSessionRepo,
    private readonly tntClient: ITntClient,
    private readonly authClient: IAuthClient,
    private readonly emailSender: EmailSender,
  ) {}

  async startSignup(input: StartSignupInput): Promise<{ sessionId: string; message: string }> {
    const domain = input.email.split("@")[1]?.toLowerCase() ?? "";
    if (DISPOSABLE_DOMAINS.has(domain)) {
      throw new SignupDomainBlockedError(domain);
    }

    const desiredSubdomain = toSlug(input.desiredSubdomain);
    if (RESERVED_SUBDOMAINS.has(desiredSubdomain)) {
      throw new SignupTenantSlugTakenError(desiredSubdomain);
    }

    if (await this.repo.existsActiveForEmail(input.email)) {
      throw new SignupEmailAlreadyRegisteredError(input.email);
    }
    if (await this.repo.existsActiveForSubdomain(desiredSubdomain)) {
      throw new SignupTenantSlugTakenError(desiredSubdomain);
    }
    if (await this.tntClient.isSlugTaken(desiredSubdomain)) {
      throw new SignupTenantSlugTakenError(desiredSubdomain);
    }

    // Register with Gen_Auth before creating any local state — a failure here
    // (including Gen_Auth's own "already registered") must not leave behind a
    // SignupSession with no corresponding authUserId.
    // Known gap (inverse case): if this succeeds but repo.create below throws,
    // the Gen_Auth user is orphaned with no SignupSession — see plan's Self-Review Notes.
    const { userId } = await this.authClient.register(input.email, input.password);

    const emailVerificationToken = generateToken(32);
    const resumeToken = generateToken(32);
    const expiresAt = new Date(Date.now() + env.EMAIL_VERIFICATION_TTL_SECS * 1000);

    const session = await this.repo.create({
      email: input.email,
      companyName: input.companyName,
      fullName: input.fullName,
      phone: input.phone,
      source: input.source,
      referralCode: input.referralCode,
      utmSource: input.utmSource,
      utmMedium: input.utmMedium,
      utmCampaign: input.utmCampaign,
      desiredSubdomain,
      authUserId: userId,
      emailVerificationTokenHash: await hashToken(emailVerificationToken),
      resumeTokenHash: await hashToken(resumeToken),
      expiresAt,
    });

    await this.emailSender.sendVerificationEmail(
      input.email,
      `/verify-email?token=${emailVerificationToken}`,
    );

    return { sessionId: session.id, message: "Check your inbox to verify your email address." };
  }

  async checkSubdomainAvailability(raw: string): Promise<SubdomainCheckResult> {
    const normalized = toSlug(raw);

    if (!isValidSlug(normalized)) {
      return { available: false, normalized, valid: false };
    }
    if (RESERVED_SUBDOMAINS.has(normalized)) {
      return { available: false, normalized, valid: true };
    }
    if (await this.repo.existsActiveForSubdomain(normalized)) {
      return { available: false, normalized, valid: true };
    }
    const takenInTnt = await this.tntClient.isSlugTaken(normalized);
    return { available: !takenInTnt, normalized, valid: true };
  }

  async resumeSignup(token: string): Promise<ResumeSignupResult> {
    const tokenHash = await hashToken(token);
    const session = await this.repo.findByResumeTokenHash(tokenHash);
    if (!session) {
      throw new ResumeTokenNotFoundError();
    }

    return {
      sessionId: session.id,
      email: session.email,
      state: session.state,
      companyName: session.companyName,
      desiredSubdomain: session.desiredSubdomain,
    };
  }
}
