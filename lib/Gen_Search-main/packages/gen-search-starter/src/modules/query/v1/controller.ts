import type { Request, Response } from "express";
import type { ZodType, ZodTypeDef } from "zod";
import type { SearchQueryInput } from "./schema.ts";
import type { SearchQueryService } from "./service.ts";

export function makeSearchController(service: SearchQueryService, schema: ZodType<SearchQueryInput, ZodTypeDef, unknown>) {
  return {
    async search(req: Request, res: Response) {
      const input = schema.parse(req.query);
      const data = await service.search(req.params.tenantId, input);
      res.json({ data });
    },
  };
}
