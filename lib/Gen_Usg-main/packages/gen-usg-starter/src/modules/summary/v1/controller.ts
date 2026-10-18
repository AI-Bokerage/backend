import type { Request, Response } from "express";
import { z } from "zod";
import type { SummaryService } from "./service.ts";

const summaryQuerySchema = z.object({ tenantId: z.string().uuid() });

export class SummaryController {
  constructor(private readonly service: SummaryService) {}

  getSummary = async (req: Request, res: Response): Promise<void> => {
    const { tenantId } = summaryQuerySchema.parse(req.query);
    const summary = await this.service.getSummary(tenantId);
    res.json(summary);
  };
}
