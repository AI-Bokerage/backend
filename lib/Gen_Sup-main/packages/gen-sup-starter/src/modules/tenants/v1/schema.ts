import { z } from "zod";

const SLUG_REGEX = /^[a-z][a-z0-9-]*[a-z0-9]$/;

export const createTenantSchema = z.object({
  name: z.string().min(2).max(255),
  slug: z.string().min(3).max(63).regex(SLUG_REGEX, "slug must start with a letter, end with a letter or digit, and contain only lowercase letters, digits, and hyphens"),
  region: z.string().min(1).max(100),
  ownerEmail: z.string().email(),
  ownerFirstName: z.string().max(100).optional().nullable(),
  ownerLastName: z.string().max(100).optional().nullable(),
  idempotencyKey: z.string().uuid().optional(),
});

export const suspendTenantSchema = z.object({
  reason: z.string().min(10).max(1000),
});

export const reactivateTenantSchema = z.object({
  note: z.string().max(500).optional(),
});

export const tenantIdParamSchema = z.object({
  id: z.string().uuid(),
});

export const tenantSlugParamSchema = z.object({
  slug: z.string().min(1),
});
