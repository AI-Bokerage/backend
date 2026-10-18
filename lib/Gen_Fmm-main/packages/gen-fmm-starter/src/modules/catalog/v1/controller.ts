import type { Request, Response } from "express";
import { z } from "zod";
import type { CatalogService } from "./service.ts";

const createModuleSchema = z.object({
  code: z.string().min(1).max(32),
  name: z.string().min(1).max(120),
  description: z.string().optional(),
  category: z.string().max(60).optional(),
  isActive: z.boolean().optional(),
  displayOrder: z.number().int().optional(),
  iconKey: z.string().max(60).optional(),
});
const updateModuleSchema = createModuleSchema.omit({ code: true }).partial();
const listModulesQuerySchema = z.object({
  category: z.string().optional(),
  // z.coerce.boolean() is wrong here: Boolean("false") is true in JS, so
  // ?isActive=false would coerce to true. Compare the raw string instead.
  isActive: z.enum(["true", "false"]).transform((v) => v === "true").optional(),
});
const createFlagSchema = z.object({
  key: z.string().min(1).max(128),
  moduleCode: z.string().max(32).optional(),
  defaultEnabled: z.boolean().optional(),
  isGradualRollout: z.boolean().optional(),
  rolloutPercentage: z.number().int().min(0).max(100).optional(),
});
const updateFlagSchema = z.object({
  moduleCode: z.string().max(32).nullable().optional(),
  defaultEnabled: z.boolean().optional(),
  isGradualRollout: z.boolean().optional(),
  rolloutPercentage: z.number().int().min(0).max(100).optional(),
});
const upsertPlanModuleSchema = z.object({
  planCode: z.string().min(1).max(60),
  moduleCode: z.string().min(1).max(32),
  entitlement: z.record(z.unknown()).optional(),
});

export class CatalogController {
  constructor(private readonly service: CatalogService) {}

  createModule = async (req: Request, res: Response): Promise<void> => {
    const input = createModuleSchema.parse(req.body);
    const module = await this.service.createModule(input);
    res.status(201).json({ module });
  };

  listModules = async (req: Request, res: Response): Promise<void> => {
    const filter = listModulesQuerySchema.parse(req.query);
    const modules = await this.service.listModules(filter);
    res.json({ modules });
  };

  updateModule = async (req: Request, res: Response): Promise<void> => {
    const input = updateModuleSchema.parse(req.body);
    const module = await this.service.updateModule(req.params.code!, input);
    res.json({ module });
  };

  createFlag = async (req: Request, res: Response): Promise<void> => {
    const input = createFlagSchema.parse(req.body);
    const flag = await this.service.createFlag(input);
    res.status(201).json({ flag });
  };

  listFlags = async (req: Request, res: Response): Promise<void> => {
    const moduleCode = typeof req.query.moduleCode === "string" ? req.query.moduleCode : undefined;
    const flags = await this.service.listFlags({ moduleCode });
    res.json({ flags });
  };

  updateFlag = async (req: Request, res: Response): Promise<void> => {
    const input = updateFlagSchema.parse(req.body);
    const flag = await this.service.updateFlag(req.params.key!, input);
    res.json({ flag });
  };

  deleteFlag = async (req: Request, res: Response): Promise<void> => {
    await this.service.deleteFlag(req.params.key!);
    res.status(204).send();
  };

  upsertPlanModule = async (req: Request, res: Response): Promise<void> => {
    const input = upsertPlanModuleSchema.parse(req.body);
    const planModule = await this.service.upsertPlanModule(input.planCode, input.moduleCode, input.entitlement ?? {});
    res.json({ planModule });
  };

  listPlanModules = async (req: Request, res: Response): Promise<void> => {
    const planModules = await this.service.listPlanModules(req.params.planCode!);
    res.json({ planModules });
  };

  deletePlanModule = async (req: Request, res: Response): Promise<void> => {
    await this.service.deletePlanModule(req.params.planCode!, req.params.moduleCode!);
    res.status(204).send();
  };
}
