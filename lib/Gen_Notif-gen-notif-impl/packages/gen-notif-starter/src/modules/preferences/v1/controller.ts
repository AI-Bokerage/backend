import type { Request, Response } from "express";
import { z } from "zod";
import type { PreferenceService } from "./service.ts";
import { listRegisteredEventTypes } from "../../templates/v1/registry.ts";

const listQuerySchema = z.object({ tenantId: z.string().uuid(), userId: z.string().uuid() });
const upsertBodySchema = z.object({
  tenantId: z.string().uuid(),
  userId: z.string().uuid(),
  eventType: z.string().min(1).max(120),
  channel: z.enum(["email", "inapp", "sms", "webhook"]),
  enabled: z.boolean(),
  digestMode: z.boolean(),
});

export class PreferenceController {
  constructor(private readonly service: PreferenceService) {}

  list = async (req: Request, res: Response): Promise<void> => {
    const { tenantId, userId } = listQuerySchema.parse(req.query);
    const prefs = await this.service.listPreferences(tenantId, userId);
    res.json({ preferences: prefs });
  };

  upsert = async (req: Request, res: Response): Promise<void> => {
    const input = upsertBodySchema.parse(req.body);
    const pref = await this.service.upsertPreference(input);
    res.json({ preference: pref });
  };

  eventTypes = async (_req: Request, res: Response): Promise<void> => {
    res.json({ eventTypes: listRegisteredEventTypes() });
  };
}
