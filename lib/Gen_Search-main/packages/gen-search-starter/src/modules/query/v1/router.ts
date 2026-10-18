import { Router } from "express";
import { makeSearchController } from "./controller.ts";
import { makeSearchQuerySchema, type SearchQueryLimits } from "./schema.ts";
import type { SearchQueryService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";
import { validateTenantId } from "../../../middleware/validate-tenant-id.ts";

export interface QueryRouterDeps {
  searchQueryService: SearchQueryService;
  internalSecretValue: string;
  limits: SearchQueryLimits;
}

export function createQueryRouter(deps: QueryRouterDeps): Router {
  const router = Router({ mergeParams: true });
  const schema = makeSearchQuerySchema(deps.limits);
  const controller = makeSearchController(deps.searchQueryService, schema);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret, validateTenantId);
  router.get("/", (req, res, next) => controller.search(req, res).catch(next));

  return router;
}
