import { z } from "zod";

export const reindexBodySchema = z.object({
  entityTypes: z.array(z.string().trim().min(1).max(64)).optional(),
});

export type ReindexInput = z.infer<typeof reindexBodySchema>;
