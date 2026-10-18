import type { Request, Response } from "express";
import { z } from "zod";
import type { IncrementService } from "./service.ts";

const incrementBodySchema = z.object({
  tenantId: z.string().uuid(),
  metric: z.string().min(1),
  delta: z.number(),
  eventId: z.string().optional(),
  resourceId: z.string().optional(),
  idempotencyKey: z.string().optional(),
  occurredAt: z.string().datetime().optional(),
});

export class IncrementController {
  constructor(private readonly service: IncrementService) {}

  increment = async (req: Request, res: Response): Promise<void> => {
    const body = incrementBodySchema.parse(req.body);
    const result = await this.service.increment({
      ...body,
      occurredAt: body.occurredAt ? new Date(body.occurredAt) : undefined,
    });
    res.json(result);
  };
}
