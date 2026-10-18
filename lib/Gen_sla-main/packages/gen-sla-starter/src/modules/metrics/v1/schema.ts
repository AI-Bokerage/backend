import { z } from "zod";

export const metricsQuerySchema = z.object({
  entityType: z.string().trim().min(1).max(64).optional(),
  from: z.string().datetime().optional(),
  to: z.string().datetime().optional(),
});

export type MetricsQuery = z.infer<typeof metricsQuerySchema>;

export const trendsQuerySchema = metricsQuerySchema.extend({
  granularity: z.enum(["day", "week", "month"]).default("day"),
});

export type TrendsQuery = z.infer<typeof trendsQuerySchema>;
