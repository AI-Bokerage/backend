import { google } from 'googleapis';

import { requireAuthenticatedClient } from './google-connection.service';
import { getPrismaClient } from '../../../config/database';
import { recordActivity } from '../../platform/activity-log';

export interface SendEmailInput {
  to: string;
  subject: string;
  body: string;
  leadId?: string;
  // Flow 4.7 — sending a saved draft removes it; if the send fails it's kept.
  draftId?: string;
}

function toRawMessage(from: string, input: SendEmailInput): string {
  const message = [
    `To: ${input.to}`,
    `From: ${from}`,
    `Subject: ${input.subject}`,
    '',
    input.body,
  ].join('\n');
  return Buffer.from(message).toString('base64url');
}

/**
 * "Sending an email" (Flow 4.7): "Email account connected?" No -> this
 * throws 400 before ever reaching here (see requireAuthenticatedClient) —
 * the diagram's "Connect Gmail or Outlook to send." Sent mail is "logged on
 * the person's timeline" — this repo's timeline is the activity log.
 */
export async function sendEmail(
  tenantId: string,
  brokerUserId: string,
  input: SendEmailInput,
): Promise<void> {
  const { client, googleEmail } = await requireAuthenticatedClient(brokerUserId);
  const gmail = google.gmail({ version: 'v1', auth: client });

  await gmail.users.messages.send({
    userId: 'me',
    requestBody: { raw: toRawMessage(googleEmail ?? 'me', input) },
  });

  if (input.draftId) {
    await getPrismaClient().emailDraft.deleteMany({
      where: { id: input.draftId, tenantId, brokerUserId },
    });
  }

  await recordActivity('crm.email.sent', {
    actorId: brokerUserId,
    targetType: 'lead',
    targetId: input.leadId,
    metadata: { tenantId, to: input.to, subject: input.subject },
  });
}
