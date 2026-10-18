// src/modules/webhooks/v1/schema.ts
import { z } from "zod";

export const WebhookProviderParamSchema = z.enum(["stripe", "razorpay"]);
