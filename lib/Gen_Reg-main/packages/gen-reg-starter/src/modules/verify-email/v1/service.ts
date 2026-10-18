// src/modules/verify-email/v1/service.ts
import type { ISignupSessionRepo } from "../../../domain/ports/signup-session.repository.port.ts";
import { SignupState } from "../../../domain/enums/signup-state.enum.ts";
import { hashToken } from "../../../common/token.ts";
import { EmailVerificationTokenInvalidError, EmailAlreadyVerifiedError } from "../../../common/errors.ts";

export interface VerifyEmailResult {
  sessionId: string;
  email: string;
  nextStep: "PLAN_SELECTION";
}

export class VerifyEmailService {
  constructor(private readonly repo: ISignupSessionRepo) {}

  async verifyEmail(token: string): Promise<VerifyEmailResult> {
    const tokenHash = await hashToken(token);
    const session = await this.repo.findByEmailVerificationTokenHash(tokenHash);

    if (!session) {
      throw new EmailVerificationTokenInvalidError();
    }
    if (session.state !== SignupState.STARTED) {
      if (session.emailVerifiedAt !== null) {
        throw new EmailAlreadyVerifiedError();
      }
      throw new EmailVerificationTokenInvalidError();
    }

    await this.repo.updateState(session.id, SignupState.EMAIL_VERIFIED, {
      emailVerifiedAt: new Date(),
      emailVerificationTokenHash: null,
    });

    return { sessionId: session.id, email: session.email, nextStep: "PLAN_SELECTION" };
  }
}
