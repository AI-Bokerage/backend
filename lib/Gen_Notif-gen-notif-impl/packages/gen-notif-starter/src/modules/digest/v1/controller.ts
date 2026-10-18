import type { Request, Response } from "express";
import type { DigestService } from "./service.ts";

export class DigestController {
  constructor(private readonly service: DigestService) {}

  sweep = async (_req: Request, res: Response): Promise<void> => {
    const result = await this.service.runSweep();
    res.json(result);
  };
}
