import type { Request, Response } from "express";
import { z } from "zod";
import type { EntitlementService } from "./service.ts";

const paramsSchema = z.object({ tenantId: z.string().uuid(), flagKey: z.string().min(1) });
const bulkParamsSchema = z.object({ tenantId: z.string().uuid() });
const planCodeQuerySchema = z.object({ planCode: z.string().optional() });

export class EntitlementController {
  constructor(private readonly service: EntitlementService) {}

  check = async (req: Request, res: Response): Promise<void> => {
    const { tenantId, flagKey } = paramsSchema.parse(req.params);
    const { planCode } = planCodeQuerySchema.parse(req.query);
    const result = await this.service.check(tenantId, flagKey, planCode);
    res.json(result);
  };

  bulk = async (req: Request, res: Response): Promise<void> => {
    const { tenantId } = bulkParamsSchema.parse(req.params);
    const { planCode } = planCodeQuerySchema.parse(req.query);
    const result = await this.service.bulk(tenantId, planCode);
    res.json(result);
  };
}
