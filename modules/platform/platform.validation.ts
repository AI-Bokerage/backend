import { z } from 'zod';

export const createBrokerageSchema = z.object({
  body: z.object({
    name: z.string().min(2).max(255),
    ownerName: z.string().min(1).max(255),
    ownerEmail: z.string().email(),
    region: z.string().min(1).max(100).default('us-east-1'),
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

export const listBrokeragesSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({
    page: z.coerce.number().int().min(0).default(0),
    size: z.coerce.number().int().min(1).max(100).default(20),
  }),
  params: z.object({}).optional(),
});

export const brokerageIdParamSchema = z.object({
  body: z.object({}).optional(),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const suspendBrokerageSchema = z.object({
  body: z.object({ reason: z.string().min(10).max(1000) }),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const reactivateBrokerageSchema = z.object({
  body: z.object({ note: z.string().max(500).optional() }),
  query: z.object({}).optional(),
  params: z.object({ id: z.string().uuid() }),
});

export const ownerAcceptedWebhookSchema = z.object({
  body: z.object({ tenantId: z.string().uuid() }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

export const authEventWebhookSchema = z.object({
  body: z.object({
    action: z.string().regex(/^(auth|security)\.[a-z_.]+$/),
    tenantId: z.string().uuid().nullable().optional(),
    userId: z.string().uuid().nullable().optional(),
    ip: z.string().max(100).nullable().optional(),
    details: z.string().max(1000).nullable().optional(),
  }),
  query: z.object({}).optional(),
  params: z.object({}).optional(),
});

export const activityLogQuerySchema = z.object({
  body: z.object({}).optional(),
  query: z.object({
    action: z.string().max(100).optional(),
    from: z.string().datetime().optional(),
    to: z.string().datetime().optional(),
    page: z.coerce.number().int().min(0).default(0),
    size: z.coerce.number().int().min(1).max(100).default(20),
  }),
  params: z.object({}).optional(),
});
