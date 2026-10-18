// src/modules/verify-email/v1/controller.ts
import type { Request, Response } from "express";
import { z } from "zod";
import type { VerifyEmailService } from "./service.ts";

const VerifyEmailQuerySchema = z.object({ token: z.string().min(1) });

export class VerifyEmailController {
  constructor(private readonly service: VerifyEmailService) {}

  verifyEmail = async (req: Request, res: Response): Promise<void> => {
    const { token } = VerifyEmailQuerySchema.parse(req.query);
    const result = await this.service.verifyEmail(token);
    res.status(200).json(result);
  };
}
