import type { Request, Response } from "express";
import { createAnnouncementSchema, listAnnouncementsQuerySchema } from "./schema.ts";
import type { AnnouncementsService } from "./service.ts";

export function makeAnnouncementsController(service: AnnouncementsService) {
  return {
    async create(req: Request, res: Response) {
      const input = createAnnouncementSchema.parse(req.body);
      const announcement = await service.create(input);
      res.status(201).json({ announcement });
    },
    async list(req: Request, res: Response) {
      const query = listAnnouncementsQuerySchema.parse(req.query);
      const result = await service.list(query);
      res.json(result);
    },
  };
}
