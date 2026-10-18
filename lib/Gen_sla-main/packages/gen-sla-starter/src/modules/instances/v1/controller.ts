import type { Request, Response } from "express";
import { listInstancesQuerySchema, instanceIdSchema } from "./schema.ts";
import type { InstanceService } from "./service.ts";

export function makeInstanceController(service: InstanceService) {
  return {
    async listInstances(req: Request, res: Response) {
      const query = listInstancesQuerySchema.parse(req.query);
      const result = await service.listInstances(req.params.tenantId, query);
      res.json(result);
    },
    async getInstance(req: Request, res: Response) {
      const { id } = instanceIdSchema.parse(req.params);
      const instance = await service.getInstance(req.params.tenantId, id);
      res.json(instance);
    },
  };
}
