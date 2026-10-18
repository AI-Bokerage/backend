import { z } from "zod";

export const ANNOUNCEMENT_TYPES = ["INFO", "MAINTENANCE", "NEW_FEATURE", "PRICING_CHANGE", "CRITICAL"] as const;
export const ANNOUNCEMENT_CHANNELS = ["EMAIL", "IN_APP"] as const;

export const createAnnouncementSchema = z.object({
  title: z.string().min(3).max(500),
  body: z.string().min(10).max(10000),
  type: z.enum(ANNOUNCEMENT_TYPES).default("INFO"),
  targetSegment: z.string().min(1).max(64).default("ALL"),
  channels: z.array(z.enum(ANNOUNCEMENT_CHANNELS)).min(1),
  scheduledFor: z.string().datetime({ offset: true }).optional(),
  createdBy: z.string().uuid(),
});

export const listAnnouncementsQuerySchema = z.object({
  type: z.enum(ANNOUNCEMENT_TYPES).optional(),
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(100).default(20),
});
