import type { Request, Response } from "express";
import {
  updateFlagSchema,
  setOverrideSchema,
  flagKeyParamSchema,
  tenantIdParamSchema,
  tenantIdFlagKeyParamSchema,
} from "./schema.ts";
import type { FeatureFlagsService } from "./service.ts";

export function makeFeatureFlagsController(service: FeatureFlagsService) {
  return {
    async listFlags(_req: Request, res: Response) {
      const flags = await service.listFlags();
      res.json({ flags });
    },
    async updateFlag(req: Request, res: Response) {
      const { key } = flagKeyParamSchema.parse(req.params);
      const input = updateFlagSchema.parse(req.body);
      const flag = await service.updateFlag(key, input);
      res.json({ flag });
    },
    async setOverride(req: Request, res: Response) {
      const { tenantId, flagKey } = tenantIdFlagKeyParamSchema.parse(req.params);
      const input = setOverrideSchema.parse(req.body);
      const override = await service.setOverride(tenantId, flagKey, input);
      res.json({ override });
    },
    async listOverridesForTenant(req: Request, res: Response) {
      const { tenantId } = tenantIdParamSchema.parse(req.params);
      const overrides = await service.listOverridesForTenant(tenantId);
      res.json({ overrides });
    },
    async clearOverride(req: Request, res: Response) {
      const { tenantId, flagKey } = tenantIdFlagKeyParamSchema.parse(req.params);
      await service.clearOverride(tenantId, flagKey);
      res.status(204).send();
    },
  };
}
