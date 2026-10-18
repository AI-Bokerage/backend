import { z } from "zod";
import { SLA_STATUSES } from "../../../config/constants.ts";

export const listInstancesQuerySchema = z.object({
  entityType: z.string().trim().min(1).max(64).optional(),
  entityId: z.string().uuid().optional(),
  status: z.enum(SLA_STATUSES).optional(),
  page: z.coerce.number().int().positive().default(1),
  pageSize: z.coerce.number().int().positive().max(100).default(20),
});

export type ListInstancesQuery = z.infer<typeof listInstancesQuerySchema>;

export const instanceIdSchema = z.object({ id: z.string().uuid() });
