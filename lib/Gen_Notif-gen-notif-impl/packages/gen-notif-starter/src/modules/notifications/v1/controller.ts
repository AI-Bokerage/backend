import type { Request, Response } from "express";
import { z } from "zod";
import type { NotificationService } from "./service.ts";

const listQuerySchema = z.object({
  tenantId: z.string().uuid(),
  userId: z.string().uuid(),
  limit: z.coerce.number().int().min(1).max(100).default(50),
  before: z.coerce.date().optional(),
});
const paramsSchema = z.object({ id: z.string().uuid() });
const bodySchema = z.object({ tenantId: z.string().uuid(), userId: z.string().uuid() });

export class NotificationController {
  constructor(private readonly service: NotificationService) {}

  listUnread = async (req: Request, res: Response): Promise<void> => {
    const { tenantId, userId, limit, before } = listQuerySchema.parse(req.query);
    const notifications = await this.service.listUnread(tenantId, userId, limit, before);
    res.json({ notifications });
  };

  markRead = async (req: Request, res: Response): Promise<void> => {
    const { id } = paramsSchema.parse(req.params);
    const { tenantId, userId } = bodySchema.parse(req.body);
    const notification = await this.service.markRead(tenantId, userId, id);
    res.json({ notification });
  };
}
