import type { Request, Response } from "express";
import { z } from "zod";
import type { OverrideService } from "./service.ts";
import { NotFoundError } from "../../../common/errors.ts";

const upsertSchema = z.object({
  tenantId: z.string().uuid(),
  flagKey: z.string().min(1).max(128),
  enabled: z.boolean(),
  config: z.record(z.unknown()).optional(),
  reason: z.string().max(300).optional(),
  expiresAt: z.coerce.date().optional(),
  createdBy: z.string().max(120).optional(),
});
const listAllQuerySchema = z.object({ tenantId: z.string().uuid().optional(), flagKey: z.string().optional() });

export class OverrideController {
  constructor(private readonly service: OverrideService) {}

  upsert = async (req: Request, res: Response): Promise<void> => {
    const input = upsertSchema.parse(req.body);
    const override = await this.service.upsert(input);
    res.json({ override });
  };

  listAll = async (req: Request, res: Response): Promise<void> => {
    const filter = listAllQuerySchema.parse(req.query);
    const overrides = await this.service.listAll(filter);
    res.json({ overrides });
  };

  listForTenant = async (req: Request, res: Response): Promise<void> => {
    const overrides = await this.service.listForTenant(req.params.tenantId!);
    res.json({ overrides });
  };

  findOne = async (req: Request, res: Response): Promise<void> => {
    const override = await this.service.findOne(req.params.tenantId!, req.params.flagKey!);
    if (!override) throw new NotFoundError("OVERRIDE_NOT_FOUND", "No override for this tenant/flag");
    res.json({ override });
  };

  delete = async (req: Request, res: Response): Promise<void> => {
    const deleted = await this.service.delete(req.params.tenantId!, req.params.flagKey!);
    if (!deleted) throw new NotFoundError("OVERRIDE_NOT_FOUND", "No override for this tenant/flag");
    res.status(204).send();
  };
}
