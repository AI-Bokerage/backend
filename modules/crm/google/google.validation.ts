import { z } from 'zod';

export const oauthCallbackSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({
    code: z.string().min(1),
    state: z.string().min(1),
  }),
  params: z.object({}).optional(),
});

export const createAppointmentSchema = z.object({
  body: z.object({
    title: z.string().min(1).max(255),
    startTime: z.string().datetime(),
    endTime: z.string().datetime(),
    leadId: z.string().uuid().optional(),
    type: z.enum(['CALL', 'CONSULTATION', 'DOCUMENT_REVIEW']).default('CONSULTATION'),
    attendeeEmails: z.array(z.string().email()).optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

export const sendEmailSchema = z.object({
  body: z.object({
    to: z.string().email(),
    subject: z.string().min(1).max(255),
    body: z.string().min(1).max(20000),
    leadId: z.string().uuid().optional(),
    draftId: z.string().uuid().optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

export const listAppointmentsSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({
    from: z.string().datetime().optional(),
    to: z.string().datetime().optional(),
  }),
  params: z.object({}).optional(),
});
