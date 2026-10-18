import type { Request, Response } from "express";
import type { CheckoutService } from "./service.ts";
import { CreateCheckoutSchema } from "./schema.ts";

export class CheckoutController {
  constructor(private readonly service: CheckoutService) {}

  createSession = async (req: Request, res: Response): Promise<void> => {
    const input = CreateCheckoutSchema.parse(req.body);
    const result = await this.service.createSession(input);
    res.status(201).json(result);
  };
}
