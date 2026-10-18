import { z } from "zod";

export const updateFlagSchema = z.object({
  moduleCode: z.string().max(32).nullable().optional(),
  defaultEnabled: z.boolean().optional(),
  isGradualRollout: z.boolean().optional(),
  rolloutPercentage: z.number().int().min(0).max(100).optional(),
  reason: z.string().min(3).max(500),
}).refine(
  (data) => data.moduleCode !== undefined || data.defaultEnabled !== undefined || data.isGradualRollout !== undefined || data.rolloutPercentage !== undefined,
  { message: "At least one flag field must be provided to update" },
);

export const setOverrideSchema = z.object({
  enabled: z.boolean(),
  config: z.record(z.unknown()).optional(),
  expiresAt: z.string().datetime({ offset: true }).optional(),
  // Capped at 300 (not 500 like updateFlagSchema.reason) because this reason
  // IS forwarded to Gen_FMM, whose own schema caps it at z.string().max(300)
  // — the flag-update reason never leaves Gen_SUP, so it isn't bound by this.
  reason: z.string().min(3).max(300),
});

export const flagKeyParamSchema = z.object({
  key: z.string().min(1).max(128),
});

export const tenantIdParamSchema = z.object({
  tenantId: z.string().uuid(),
});

export const tenantIdFlagKeyParamSchema = z.object({
  tenantId: z.string().uuid(),
  flagKey: z.string().min(1).max(128),
});
