import type { Request, Response } from "express";
import { z } from "zod";
import type { CheckService } from "./service.ts";

const checkBodySchema = z.object({
  tenantId: z.string().uuid(),
  metric: z.string().min(1),
  delta: z.number().optional(),
});

export class CheckController {
  constructor(private readonly service: CheckService) {}

  check = async (req: Request, res: Response): Promise<void> => {
    const body = checkBodySchema.parse(req.body);
    const result = await this.service.check(body.tenantId, body.metric, body.delta);
    // Always 200 — the verdict lives in the body, never in the status code.
    res.json(result);
  };
}
