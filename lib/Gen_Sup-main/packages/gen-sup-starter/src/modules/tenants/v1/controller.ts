import type { Request, Response } from "express";
import { createTenantSchema, suspendTenantSchema, reactivateTenantSchema, tenantIdParamSchema, tenantSlugParamSchema } from "./schema.ts";
import type { TenantsService } from "./service.ts";

export function makeTenantsController(service: TenantsService) {
  return {
    async create(req: Request, res: Response) {
      const input = createTenantSchema.parse(req.body);
      const result = await service.create(input);
      res.status(201).json(result);
    },
    async getById(req: Request, res: Response) {
      const { id } = tenantIdParamSchema.parse(req.params);
      const result = await service.getById(id);
      res.json(result);
    },
    async getBySlug(req: Request, res: Response) {
      const { slug } = tenantSlugParamSchema.parse(req.params);
      const result = await service.getBySlug(slug);
      res.json(result);
    },
    async suspend(req: Request, res: Response) {
      const { id } = tenantIdParamSchema.parse(req.params);
      const { reason } = suspendTenantSchema.parse(req.body);
      const result = await service.suspend(id, reason);
      res.json(result);
    },
    async reactivate(req: Request, res: Response) {
      const { id } = tenantIdParamSchema.parse(req.params);
      const { note } = reactivateTenantSchema.parse(req.body);
      const result = await service.reactivate(id, note);
      res.json(result);
    },
  };
}
