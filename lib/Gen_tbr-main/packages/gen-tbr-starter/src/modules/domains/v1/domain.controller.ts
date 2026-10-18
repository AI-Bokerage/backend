import type { Request, Response } from "express";
import { z } from "zod";
import type { DomainService } from "./domain.service.ts";

const claimBodySchema = z.object({
  tenantId: z.string().uuid(),
  domain: z.string().min(1).max(253),
  verificationMethod: z.enum(["TXT", "CNAME"]).optional(),
});

const primaryBodySchema = z.object({
  tenantId: z.string().uuid(),
});

export function makeDomainController(service: DomainService) {
  return {
    async create(req: Request, res: Response) {
      const body = claimBodySchema.parse(req.body);
      const record = await service.claim(body);
      res.status(201).json(record);
    },

    async list(req: Request, res: Response) {
      const tenantId = z.string().uuid().parse(req.query.tenantId);
      const records = await service.list(tenantId);
      res.json(records);
    },

    async verify(req: Request, res: Response) {
      const body = primaryBodySchema.parse(req.body);
      const record = await service.verify(body.tenantId, req.params.id);
      res.json(record);
    },

    async activate(req: Request, res: Response) {
      const body = primaryBodySchema.parse(req.body);
      const record = await service.activate(body.tenantId, req.params.id);
      res.json(record);
    },

    async detach(req: Request, res: Response) {
      const body = primaryBodySchema.parse(req.body);
      const record = await service.detach(body.tenantId, req.params.id);
      res.json(record);
    },

    async primary(req: Request, res: Response) {
      const body = primaryBodySchema.parse(req.body);
      const record = await service.setPrimary(body.tenantId, req.params.id);
      res.json(record);
    },
  };
}
