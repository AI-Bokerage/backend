import type { Request, Response } from "express";
import { erasureBodySchema } from "./schema.ts";
import type { ErasureService } from "./service.ts";

export function makeErasureController(service: ErasureService) {
  return {
    async removeUserDocuments(req: Request, res: Response) {
      const input = erasureBodySchema.parse(req.body);
      const result = await service.removeUserDocuments(req.params.tenantId, input.ownerId);
      res.status(200).json(result);
    },
  };
}
