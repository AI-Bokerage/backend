import { z } from 'zod';

export const createLeadSchema = z.object({
  body: z.object({
    name: z.string().min(1).max(255),
    email: z.string().email().optional(),
    phone: z.string().max(50).optional(),
    income: z.coerce.number().nonnegative().optional(),
    monthlyDebts: z.coerce.number().nonnegative().optional(),
    downPayment: z.coerce.number().nonnegative().optional(),
    propertyAddress: z.string().max(500).optional(),
    propertyValue: z.coerce.number().nonnegative().optional(),
    notes: z.string().max(5000).optional(),
    tags: z.array(z.string().min(1).max(50)).max(20).optional(),
    dateOfBirth: z.string().datetime().optional(),
    sin: z.string().min(1).max(50).optional(),
    bankDetails: z.string().min(1).max(1000).optional(),
    // Flow 4.2 "Save" after an AI review: what the broker approved in that run is applied too.
    aiRunId: z.string().uuid().optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

export const updateNotesSchema = z.object({
  body: z.object({ brokerNotes: z.string().max(10000) }),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const updateTagsSchema = z.object({
  body: z.object({ tags: z.array(z.string().min(1).max(50)).max(20) }),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const updateLeadProfileSchema = z.object({
  body: z.object({
    name: z.string().min(1).max(255).optional(),
    email: z.string().email().optional(),
    phone: z.string().max(50).optional(),
    city: z.string().max(255).optional(),
    fileStatus: z.enum(['ACTIVE', 'INACTIVE']).optional(),
    propertyAddress: z.string().max(500).optional(),
    propertyValue: z.coerce.number().nonnegative().optional(),
    income: z.coerce.number().nonnegative().optional(),
    monthlyDebts: z.coerce.number().nonnegative().optional(),
    downPayment: z.coerce.number().nonnegative().optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const revealFieldSchema = z.object({
  body: z.object({ field: z.enum(['sin', 'bankDetails', 'dateOfBirth']) }),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const recalculateAffordabilitySchema = z.object({
  body: z.object({ leadIds: z.array(z.string().uuid()).optional() }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

export const leadIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

const LEAD_STAGES = [
  'NEW',
  'QUALIFIED',
  'APPLICATION',
  'UNDERWRITING',
  'APPROVED',
  'FUNDED',
  'LOST',
] as const;

export const updateStageSchema = z.object({
  body: z.object({
    stage: z.enum(LEAD_STAGES),
    lostReason: z.string().min(1).max(1000).optional(),
    lostNote: z.string().max(5000).optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

const mortgageBody = z.object({
  lender: z.string().min(1).max(255),
  interestRate: z.coerce.number().positive(),
  balance: z.coerce.number().positive(),
  monthlyPayment: z.coerce.number().positive(),
  maturityDate: z.string().datetime(),
});

export const fundLeadSchema = z.object({
  body: mortgageBody,
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const addMortgageSchema = fundLeadSchema;

export const reassignSchema = z.object({
  body: z.object({ toBrokerUserId: z.string().uuid() }),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const createTaskSchema = z.object({
  body: z.object({
    title: z.string().min(1).max(255),
    leadId: z.string().uuid().optional(),
    dueDate: z.string().datetime().optional(),
    priority: z.enum(['LOW', 'MEDIUM', 'HIGH']).optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

export const taskIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

const REPORT_TYPES = ['pipeline', 'renewals', 'team-performance', 'ai-usage'] as const;

export const reportQuerySchema = z.object({
  body: z.object({}).optional(),
  query: z.object({
    type: z.enum(REPORT_TYPES),
    from: z.string().datetime().optional(),
    to: z.string().datetime().optional(),
    brokerId: z.string().uuid().optional(),
    teamId: z.string().uuid().optional(),
  }),
  params: z.object({}).optional(),
});

export const updateProfileSchema = z.object({
  body: z.object({
    displayName: z.string().max(255).optional(),
    supportEmail: z.string().email().optional(),
    phone: z.string().max(50).optional(),
    address: z.string().max(500).optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

const SLUG = /^[a-z0-9-]+$/;

export const updateBookingSettingsSchema = z.object({
  body: z.object({
    slug: z
      .string()
      .min(3)
      .max(100)
      .regex(SLUG, 'lowercase letters, numbers and hyphens only')
      .optional(),
    enabled: z.boolean().optional(),
    bufferMinutes: z.coerce.number().int().min(0).max(240).optional(),
    defaultBrokerUserId: z.string().uuid().nullable().optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

export const exportJobIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: z.object({ jobId: z.string().uuid() }),
});

export const internalLeadCountSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({ tenantId: z.string().uuid() }),
  params: z.object({ userId: z.string().uuid() }),
});

export const reassignAllSchema = z.object({
  body: z.object({ toBrokerUserId: z.string().uuid() }),
  query: z.object({}).optional(),
  params: z.object({ userId: z.string().uuid() }),
});

export const deleteLeadSchema = z.object({
  body: z.object({ confirmName: z.string().min(1).max(255) }),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const linkTaskSchema = z.object({
  body: z.object({ leadId: z.string().uuid() }),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const searchSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({ q: z.string().min(1).max(100) }),
  params: z.object({}).optional(),
});

export const internalProfileStatusSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: z.object({ tenantId: z.string().uuid() }),
});

// Flow 4 dashboard cards → filtered lists (see dashboard.service.ts links).
const withinDays = z.coerce.number().int().min(0).max(3650).optional();
const flag = z
  .enum(['true', 'false'])
  .transform((v) => v === 'true')
  .optional();

export const listClientsSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({
    renewalWithinDays: withinDays,
    followUpWithinDays: withinDays,
    birthdayWithinDays: withinDays,
    missingDocuments: flag,
  }),
  params: z.object({}).optional(),
});

export const listLeadsSchema = z.object({
  body: z.object({}).optional(),
  query: listClientsSchema.shape.query.extend({
    // Comma-separated stages, or ALL for every stage (default: the active pipeline).
    stage: z
      .string()
      .transform((v) => v.split(','))
      .pipe(z.array(z.enum([...LEAD_STAGES, 'ALL'])))
      .optional(),
    hot: flag,
  }),
  params: z.object({}).optional(),
});

export const listTasksSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({ due: z.enum(['today', 'overdue']).optional() }),
  params: z.object({}).optional(),
});
