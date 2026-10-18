import { z } from 'zod';

import { EMAIL_INTENTS, type EmailIntent } from './ai-review.service';

const leadParams = z.object({ id: z.string().uuid() });
const emailIntent = z.enum(Object.keys(EMAIL_INTENTS) as [EmailIntent, ...EmailIntent[]]);
export const aiLeadSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: leadParams,
});

// The AI services' id format (voice notes; notes-svc runs are UUIDs).
const aiId = z.string().regex(/^[A-Za-z0-9_-]{1,64}$/);

export const aiIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: z.object({ id: aiId }),
});

export const aiReviewDraftNotesSchema = z.object({
  body: z.object({
    // notes-svc's MAX_NOTE_CHARS.
    notes: z.string().trim().min(1).max(60000),
    // What the form already holds: masked before the AI sees it, and never silently overwritten.
    fields: z
      .object({
        name: z.string().max(255),
        email: z.string().email(),
        phone: z.string().max(50),
        income: z.coerce.number().nonnegative(),
        downPayment: z.coerce.number().nonnegative(),
        dateOfBirth: z.string().datetime(),
      })
      .partial()
      .optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

/** "Accept, edit or reject each item" — notes-svc's decision list, checked again there. */
export const aiRunDecisionsSchema = z.object({
  body: z.object({
    decisions: z
      .array(
        z
          .object({
            suggestionId: aiId,
            decision: z.enum(['approved', 'edited', 'rejected', 'pending']),
          })
          .passthrough(),
      )
      .min(1)
      .max(300),
  }),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const aiDraftEmailSchema = z.object({
  body: z.object({
    intent: emailIntent,
    // Why you're writing — a follow-up needs it (or there is nothing to say).
    purpose: z.string().trim().min(1).max(1000).optional(),
    tone: z.enum(['professional', 'warm', 'concise']).optional(),
  }),
  query: z.object({}).optional(),
  params: leadParams,
});

export const aiEmailDraftSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({ version: z.coerce.number().int().min(1).optional() }),
  params: z.object({ id: aiId }),
});

export const aiRefineEmailSchema = z.object({
  body: z.object({
    instruction: z.string().trim().min(1).max(500),
    fromVersion: z.number().int().min(1).optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({ id: aiId }),
});

export const aiApplyReviewSchema = z.object({
  body: z.object({ aiRunId: z.string().uuid() }),
  query: z.object({}).optional(),
  params: leadParams,
});

export const aiAssistantSchema = z.object({
  body: z.object({
    question: z.string().trim().min(1).max(2000),
    // The conversation so far (the frontend keeps it; nothing is stored).
    history: z
      .array(z.object({ role: z.enum(['user', 'assistant']), content: z.string().min(1).max(4000) }))
      .max(20)
      .optional(),
    // The person whose page the question was asked on, if any.
    clientId: z.string().uuid().optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

export const aiSearchSchema = z.object({
  body: z.object({ query: z.string().trim().min(2).max(500) }).strict(),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

// Dashboard "AI suggestions" → "Add as task", optionally changed first.
export const aiRecommendationTaskSchema = z.object({
  body: z
    .object({
      title: z.string().trim().min(1).max(120).optional(),
      dueDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable().optional(),
      priority: z.enum(['low', 'medium', 'high', 'urgent']).optional(),
    })
    .strict(),
  query: z.object({}).optional(),
  params: z.object({ id: aiId }),
});

export const createEmailDraftSchema = z.object({
  body: z.object({
    intent: emailIntent.optional(),
    subject: z.string().min(1).max(255),
    body: z.string().min(1).max(20000),
    aiAssisted: z.boolean().optional(),
  }),
  query: z.object({}).optional(),
  params: leadParams,
});

export const updateEmailDraftSchema = z.object({
  body: z.object({
    intent: emailIntent.optional(),
    subject: z.string().min(1).max(255).optional(),
    body: z.string().min(1).max(20000).optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({ draftId: z.string().uuid() }),
});

export const updateSettingsSchema = z.object({
  body: z.object({ aiEnabled: z.boolean() }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

const DOCUMENT_TYPES = [
  'ID',
  'T4',
  'NOA',
  'PAY_STUB',
  'EMPLOYMENT_LETTER',
  'BANK_STATEMENT',
  'PURCHASE_AGREEMENT',
  'MORTGAGE_STATEMENT',
  'PROPERTY_TAX',
  'OTHER',
] as const;

export const setRequiredDocumentsSchema = z.object({
  body: z.object({ types: z.array(z.enum(DOCUMENT_TYPES)).max(DOCUMENT_TYPES.length) }),
  query: z.object({}).optional(),
  params: leadParams,
});

const uploadQuery = z.object({
  type: z.enum(DOCUMENT_TYPES),
  fileName: z.string().min(1).max(255),
});

export const uploadDocumentSchema = z.object({
  query: uploadQuery,
  params: leadParams,
});

export const requestDocumentsSchema = z.object({
  body: z.object({ useSystemAddress: z.boolean().optional() }),
  query: z.object({}).optional(),
  params: leadParams,
});

export const documentIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: z.object({ documentId: z.string().uuid() }),
});

/** Flow 4.5 "Accept, edit or reject each value" — docread decisions, checked again there. */
export const documentDecisionsSchema = z.object({
  body: z.object({
    decisions: z
      .array(
        z
          .object({ fieldId: aiId, decision: z.enum(['approved', 'edited', 'rejected', 'pending']) })
          .passthrough(),
      )
      .min(1)
      .max(200),
  }),
  query: z.object({}).optional(),
  params: z.object({ documentId: z.string().uuid() }),
});

export const documentMortgageSchema = z.object({
  body: z.union([
    z.object({ mortgageId: z.string().uuid() }).strict(),
    z.object({ createNew: z.literal(true) }).strict(),
  ]),
  query: z.object({}).optional(),
  params: z.object({ documentId: z.string().uuid() }),
});

export const publicTokenSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: z.object({ token: z.string().regex(/^[a-f0-9]{64}$/) }),
});

export const publicUploadSchema = z.object({
  query: uploadQuery,
  params: z.object({ token: z.string().regex(/^[a-f0-9]{64}$/) }),
});

export const publicBookingSchema = z.object({
  body: z
    .object({
      bookingRef: z.string().min(1).max(200),
      name: z.string().min(1).max(255),
      email: z.string().email(),
      phone: z.string().max(50).optional(),
      title: z.string().max(255).optional(),
      startTime: z.string().datetime(),
      endTime: z.string().datetime(),
    })
    .refine((b) => new Date(b.endTime) > new Date(b.startTime), {
      message: 'endTime must be after startTime',
    }),
  query: z.object({}).optional(),
  params: z.object({ slug: z.string().min(3).max(100) }),
});
