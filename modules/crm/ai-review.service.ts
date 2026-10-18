
/**
 * Shared AI wording. The AI features themselves: voice notes, the notes
 * review and 4.9 suggested tasks in ai-notes.service.ts, document reading in
 * ai-documents.service.ts, email drafts in ai-email.service.ts, the 4.10
 * assistant in ai-assistant.service.ts.
 */

export const AI_DISCLOSURE =
  'AI-generated suggestions. Review before using — nothing is saved until you apply it.';

/** Flow 4.7 "Pick the purpose" — shared by the email screen and the AI tools tab. */
export const EMAIL_INTENTS = {
  FOLLOW_UP: 'Follow-up',
  RENEWAL: 'Renewal',
  BIRTHDAY: 'Birthday',
  DOCUMENTS: 'Documents',
  ANNUAL_REVIEW: 'Annual review',
  REFINANCE: 'Refinance',
} as const;
export type EmailIntent = keyof typeof EMAIL_INTENTS;
