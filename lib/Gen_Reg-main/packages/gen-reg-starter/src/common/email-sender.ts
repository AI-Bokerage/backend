// src/common/email-sender.ts
import { logger } from "./logger.ts";

export interface EmailSender {
  sendVerificationEmail(to: string, verifyUrl: string): Promise<void>;
}

// ponytail: stdout stub, not real SES/SMTP — swap the implementation
// (same interface) when Phase 1 needs actual email delivery.
export class ConsoleEmailSender implements EmailSender {
  async sendVerificationEmail(to: string, verifyUrl: string): Promise<void> {
    logger.info({ to, verifyUrl }, "[email] verification link (console stub)");
  }
}
