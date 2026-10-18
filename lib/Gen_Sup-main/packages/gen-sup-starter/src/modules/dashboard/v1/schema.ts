import { z } from "zod";

export const dashboardKpisQuerySchema = z.object({
  forceRefresh: z
    .enum(["true", "false"])
    .optional()
    .transform((v) => v === "true"),
});
