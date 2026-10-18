// src/modules/select-plan/v1/schema.ts
import { z } from "zod";

export const SelectPlanSchema = z.object({
  sessionId: z.string().uuid(),
  planCode: z.string().min(1).max(64),
  billingCycle: z.enum(["MONTHLY", "ANNUAL"]).default("MONTHLY"),
});

export type SelectPlanInput = z.infer<typeof SelectPlanSchema>;
