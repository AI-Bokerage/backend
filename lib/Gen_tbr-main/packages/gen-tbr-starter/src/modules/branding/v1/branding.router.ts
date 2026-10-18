import { Router } from "express";
import multer from "multer";
import { makeBrandingController } from "./branding.controller.ts";
import type { BrandingService } from "./branding.service.ts";
import type { IAssetStore } from "../../../domain/ports/asset-store.port.ts";
import { internalSecret } from "../../../middleware/internal-secret.ts";
import { validateTenantId } from "../../../middleware/validate-tenant-id.ts";

export interface BrandingRouterDeps {
  brandingService: BrandingService;
  assetStore?: IAssetStore;
  internalSecretValue: string;
  assetsEnabled: boolean;
  // Defaults to true so existing callers that don't pass it keep the manifest
  // routes mounted; createGenTbr wires modules.manifest through here so a
  // disabled manifest module actually 404s instead of just being undocumented.
  manifestEnabled?: boolean;
}

export function createBrandingRouter(deps: BrandingRouterDeps): Router {
  const router = Router();
  const controller = makeBrandingController(deps.brandingService, deps.assetStore);
  const requireSecret = internalSecret(deps.internalSecretValue);
  const upload = multer({ storage: multer.memoryStorage() });

  const manifestEnabled = deps.manifestEnabled ?? true;

  // Registered unconditionally (not gated behind an `if`) so that when the
  // manifest module is disabled, these paths still 404 here instead of
  // falling through to the broader "/:tenantId" route below (which would
  // otherwise match "/manifest" with tenantId="manifest" and return a
  // confusing 401/400 instead of a clean 404).
  router.get("/:tenantId/manifest", validateTenantId, (req, res, next) => {
    if (!manifestEnabled) {
      res.status(404).json({ error: "Not Found" });
      return;
    }
    controller.manifest(req, res).catch(next);
  });
  router.get("/manifest", (req, res, next) => {
    if (!manifestEnabled) {
      res.status(404).json({ error: "Not Found" });
      return;
    }
    controller.manifest(req, res).catch(next);
  });

  router.get("/:tenantId", requireSecret, validateTenantId, (req, res, next) => {
    controller.get(req, res).catch(next);
  });
  router.put("/:tenantId", requireSecret, validateTenantId, (req, res, next) => {
    controller.upsert(req, res).catch(next);
  });
  router.patch("/:tenantId", requireSecret, validateTenantId, (req, res, next) => {
    controller.patch(req, res).catch(next);
  });

  if (deps.assetsEnabled) {
    router.post(
      "/:tenantId/assets",
      requireSecret,
      validateTenantId,
      upload.single("file"),
      (req, res, next) => {
        controller.uploadAsset(req, res).catch(next);
      },
    );
    router.get("/:tenantId/assets/:assetId", requireSecret, validateTenantId, (req, res, next) => {
      controller.getAsset(req, res).catch(next);
    });
  }

  return router;
}
