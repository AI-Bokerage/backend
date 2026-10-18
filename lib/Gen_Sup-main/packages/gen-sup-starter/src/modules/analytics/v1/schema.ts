import { z } from "zod";

const SNAPSHOT_PERIODS = ["daily", "weekly", "monthly"] as const;

export const revenueQuerySchema = z.object({
  planCode: z.string().optional(),
  region: z.string().optional(),
  from: z.string().datetime({ offset: true }).optional(),
  to: z.string().datetime({ offset: true }).optional(),
  export: z.enum(["csv"]).optional(),
});

export const revenueHistoryQuerySchema = z.object({
  period: z.enum(SNAPSHOT_PERIODS),
  from: z.string().datetime({ offset: true }).optional(),
  to: z.string().datetime({ offset: true }).optional(),
});

export const usageQuerySchema = z.object({
  tenantIds: z
    .string()
    .transform((s) => s.split(",").map((id) => id.trim()))
    .pipe(z.array(z.string().uuid()).min(1)),
});
