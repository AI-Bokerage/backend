// src/modules/webhooks/v1/controller.ts
import type { Request, Response } from "express";
import type { WebhooksService } from "./service.ts";
import { WebhookProviderParamSchema } from "./schema.ts";

export class WebhooksController {
  constructor(private readonly service: WebhooksService) {}

  handleWebhook = async (req: Request, res: Response): Promise<void> => {
    const provider = WebhookProviderParamSchema.parse(req.params.provider);
    const signature = req.header(this.service.getWebhookSignatureHeader(provider)) ?? "";
    const rawBody = req.body as Buffer;

    const event = this.service.verifyAndNormalize(provider, rawBody, signature);
    await this.service.handleEvent(event);

    res.status(200).json({ received: true });
  };
}
