import type { Request, Response } from "express";
import { z } from "zod";
import type { TelemetryService } from "./service.ts";

const queryFilterSchema = z.object({
  flagKey: z.string().optional(),
  from: z.coerce.date().optional(),
  to: z.coerce.date().optional(),
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(200).default(50),
});

export class TelemetryController {
  constructor(private readonly service: TelemetryService) {}

  query = async (req: Request, res: Response): Promise<void> => {
    const filter = queryFilterSchema.parse(req.query);
    const result = await this.service.query(req.params.tenantId!, filter);
    res.json(result);
  };
}
