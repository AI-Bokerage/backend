import type { Request, Response } from "express";
import { z } from "zod";
import type { WebhookEndpointService } from "./service.ts";

const listQuerySchema = z.object({ tenantId: z.string().uuid(), userId: z.string().uuid() });
const createBodySchema = z.object({
  tenantId: z.string().uuid(),
  userId: z.string().uuid(),
  url: z.string().url().max(2048),
  description: z.string().max(300).optional(),
});
const updateBodySchema = z.object({
  tenantId: z.string().uuid(),
  url: z.string().url().max(2048).optional(),
  enabled: z.boolean().optional(),
  description: z.string().max(300).optional(),
});
const paramsSchema = z.object({ id: z.string().uuid() });
const deleteBodySchema = z.object({ tenantId: z.string().uuid() });

export class WebhookEndpointController {
  constructor(private readonly service: WebhookEndpointService) {}

  list = async (req: Request, res: Response): Promise<void> => {
    const { tenantId, userId } = listQuerySchema.parse(req.query);
    const endpoints = await this.service.list(tenantId, userId);
    res.json({ endpoints });
  };

  create = async (req: Request, res: Response): Promise<void> => {
    const input = createBodySchema.parse(req.body);
    const endpoint = await this.service.register(input);
    res.status(201).json({ endpoint });
  };

  update = async (req: Request, res: Response): Promise<void> => {
    const { id } = paramsSchema.parse(req.params);
    const { tenantId, ...rest } = updateBodySchema.parse(req.body);
    const endpoint = await this.service.update(tenantId, id, rest);
    res.json({ endpoint });
  };

  remove = async (req: Request, res: Response): Promise<void> => {
    const { id } = paramsSchema.parse(req.params);
    const { tenantId } = deleteBodySchema.parse(req.body);
    await this.service.delete(tenantId, id);
    res.status(204).send();
  };
}
