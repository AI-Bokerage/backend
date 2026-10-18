import { z } from "zod";

export interface SearchQueryLimits {
  minQueryLength: number;
  maxQueryLength: number;
  defaultResults: number;
  maxResults: number;
  maxOffset: number;
}

// A factory, not a module-level constant, because min/max query length and
// result limits are per-instance config (createGenSearch({ limits })), not
// fixed constants the way MAX_PAGE_SIZE is in Gen_SLA.
export function makeSearchQuerySchema(limits: SearchQueryLimits) {
  return z.object({
    q: z.string().trim().min(limits.minQueryLength).max(limits.maxQueryLength),
    type: z.string().trim().min(1).max(64).optional(),
    roleId: z.string().trim().max(120).optional(),
    limit: z.coerce.number().int().positive().max(limits.maxResults).default(limits.defaultResults),
    offset: z.coerce.number().int().nonnegative().max(limits.maxOffset).default(0),
  });
}

export type SearchQueryInput = z.infer<ReturnType<typeof makeSearchQuerySchema>>;
