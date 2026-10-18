// src/modules/signup/v1/controller.ts
import type { Request, Response } from "express";
import type { SignupService } from "./service.ts";
import { StartSignupSchema, SubdomainCheckSchema, ResumeSignupSchema } from "./schema.ts";

export class SignupController {
  constructor(private readonly service: SignupService) {}

  startSignup = async (req: Request, res: Response): Promise<void> => {
    const input = StartSignupSchema.parse(req.body);
    const result = await this.service.startSignup(input);
    res.status(201).json(result);
  };

  checkSubdomain = async (req: Request, res: Response): Promise<void> => {
    const { value } = SubdomainCheckSchema.parse(req.query);
    const result = await this.service.checkSubdomainAvailability(value);
    res.status(200).json(result);
  };

  resumeSignup = async (req: Request, res: Response): Promise<void> => {
    const { token } = ResumeSignupSchema.parse(req.query);
    const result = await this.service.resumeSignup(token);
    res.status(200).json(result);
  };
}
