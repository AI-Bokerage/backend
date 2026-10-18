import { z } from "zod";

export const createPolicySchema = z
  .object({
    name: z.string().trim().min(1).max(128),
    entityType: z.string().trim().min(1).max(64),
    slaType: z.string().trim().min(1).max(64),
    durationMins: z.number().int().positive(),
    warningMins: z.number().int().positive(),
    isEnabled: z.boolean().default(true),
    description: z.string().trim().max(1024).optional(),
    createdBy: z.string().trim().max(120).optional(),
  })
  .refine((d) => d.warningMins < d.durationMins, {
    message: "warningMins must be less than durationMins",
    path: ["warningMins"],
  });

export type CreatePolicyInput = z.infer<typeof createPolicySchema>;

export const updatePolicySchema = z
  .object({
    name: z.string().trim().min(1).max(128).optional(),
    durationMins: z.number().int().positive().optional(),
    warningMins: z.number().int().positive().optional(),
    isEnabled: z.boolean().optional(),
    description: z.string().trim().max(1024).nullable().optional(),
    updatedBy: z.string().trim().max(120).optional(),
  })
  .refine((d) => Object.keys(d).length > 0, { message: "At least one field is required" });

export type UpdatePolicyInput = z.infer<typeof updatePolicySchema>;

export const listPoliciesQuerySchema = z.object({
  entityType: z.string().trim().min(1).max(64).optional(),
  isEnabled: z.string().transform((v) => v === "true").optional(),
  page: z.coerce.number().int().positive().default(1),
  pageSize: z.coerce.number().int().positive().max(100).default(20),
});

export type ListPoliciesQuery = z.infer<typeof listPoliciesQuerySchema>;

export const policyIdSchema = z.object({ id: z.string().uuid() });
