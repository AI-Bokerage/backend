import { Router } from "express";
import { makeReindexController } from "./controller.ts";
import type { ReindexService } from "./service.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";
import { validateTenantId } from "../../../middleware/validate-tenant-id.ts";

export interface ReindexRouterDeps {
  reindexService: ReindexService;
  internalSecretValue: string;
}

// No RBAC — the LLD gates reindex to Super Admin, but this library has no
// role model. Gate who can reach this route in front of it (own gateway,
// second internal secret, whatever your host's authz already does) — see
// docs/source-audit-notes.md.
export function createReindexRouter(deps: ReindexRouterDeps): Router {
  const router = Router({ mergeParams: true });
  const controller = makeReindexController(deps.reindexService);
  const requireSecret = internalSecret(deps.internalSecretValue);

  router.use(requireSecret, validateTenantId);
  router.post("/", (req, res, next) => controller.reindex(req, res).catch(next));

  return router;
}
