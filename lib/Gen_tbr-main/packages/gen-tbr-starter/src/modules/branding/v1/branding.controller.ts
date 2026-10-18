import type { Request, Response } from "express";
import { randomUUID } from "node:crypto";
import { z } from "zod";
import type { BrandingService } from "./branding.service.ts";
import type { IAssetStore } from "../../../domain/ports/asset-store.port.ts";
import { AssetNotFoundError } from "../../../common/errors.ts";

const upsertBodySchema = z.object({
  displayName: z.string().min(1).max(120),
  tagline: z.string().max(200).nullish(),
  logoUrl: z.string().url().nullish(),
  logoDarkUrl: z.string().url().nullish(),
  faviconUrl: z.string().url().nullish(),
  primaryColor: z.string().nullish(),
  secondaryColor: z.string().nullish(),
  accentColor: z.string().nullish(),
  fontFamily: z.string().max(120).nullish(),
  theme: z.enum(["SYSTEM", "LIGHT", "DARK"]).optional(),
  rawMeta: z.unknown().optional(),
});

const patchBodySchema = upsertBodySchema.partial();

export function makeBrandingController(service: BrandingService, assetStore?: IAssetStore) {
  return {
    async get(req: Request, res: Response) {
      const record = await service.get(req.params.tenantId);
      res.json(record);
    },

    async upsert(req: Request, res: Response) {
      const body = upsertBodySchema.parse(req.body);
      const record = await service.upsert({ tenantId: req.params.tenantId, ...body });
      res.json(record);
    },

    async patch(req: Request, res: Response) {
      const body = patchBodySchema.parse(req.body);
      const record = await service.patch(req.params.tenantId, body);
      res.json(record);
    },

    async manifest(req: Request, res: Response) {
      const domain = typeof req.query.domain === "string" ? req.query.domain : undefined;
      const record = domain
        ? await service.getManifest({ domain })
        : await service.getManifest({ tenantId: req.params.tenantId });
      res.json(record);
    },

    async uploadAsset(req: Request, res: Response) {
      if (!assetStore) throw new AssetNotFoundError("assets module disabled");
      const file = (req as Request & { file?: Express.Multer.File }).file;
      if (!file) throw new AssetNotFoundError("no file uploaded");
      const assetId = randomUUID();
      const result = await assetStore.put({
        assetId,
        tenantId: req.params.tenantId,
        contentType: file.mimetype,
        bytes: file.buffer,
      });
      res.json(result);
    },

    async getAsset(req: Request, res: Response) {
      if (!assetStore) throw new AssetNotFoundError(req.params.assetId);
      const found = await assetStore.get(req.params.tenantId, req.params.assetId);
      if (!found) throw new AssetNotFoundError(req.params.assetId);
      res.json(found);
    },
  };
}
