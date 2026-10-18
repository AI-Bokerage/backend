import type { Request, Response } from "express";
import { reindexBodySchema } from "./schema.ts";
import type { ReindexService } from "./service.ts";

export function makeReindexController(service: ReindexService) {
  return {
    async reindex(req: Request, res: Response) {
      const input = reindexBodySchema.parse(req.body ?? {});
      const result = await service.reindex(req.params.tenantId, input.entityTypes);
      res.status(200).json(result);
    },
  };
}
