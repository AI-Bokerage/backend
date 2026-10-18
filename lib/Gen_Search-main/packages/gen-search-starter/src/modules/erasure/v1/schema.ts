import { z } from "zod";

export const erasureBodySchema = z.object({
  ownerId: z.string().trim().uuid(),
});

export type ErasureInput = z.infer<typeof erasureBodySchema>;
