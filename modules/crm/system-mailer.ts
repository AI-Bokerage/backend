import { logger } from '../../config/logger';

/**
 * Flow 4.5 "send from the system address instead" when the broker's own
 * email isn't connected. shortcut: logs only (STUB) — wire an SMTP or
 * transactional-email provider before go-live; callers don't change.
 */
export async function sendSystemEmail(message: { to: string; subject: string; body: string }) {
  logger.info({ to: message.to, subject: message.subject }, 'crm.system_email.stub_send');
  return { stub: true as const };
}
