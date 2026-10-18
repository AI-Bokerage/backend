// src/modules/select-plan/v1/controller.ts
import type { Request, Response } from "express";
import type { SelectPlanService } from "./service.ts";
import { SelectPlanSchema } from "./schema.ts";

export class SelectPlanController {
  constructor(private readonly service: SelectPlanService) {}

  selectPlan = async (req: Request, res: Response): Promise<void> => {
    const input = SelectPlanSchema.parse(req.body);
    const result = await this.service.selectPlan(input);
    res.status(200).json(result);
  };
}
