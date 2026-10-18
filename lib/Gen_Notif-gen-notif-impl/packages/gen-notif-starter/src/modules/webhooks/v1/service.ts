import { randomBytes } from "node:crypto";
import type {
  IWebhookEndpointRepo,
  WebhookEndpointRecord,
  UpdateWebhookEndpointInput,
} from "../../../domain/ports/webhook-endpoint.repository.port.ts";

export interface RegisterWebhookInput {
  tenantId: string;
  userId: string;
  url: string;
  description?: string;
}

export class WebhookEndpointService {
  constructor(private readonly repo: IWebhookEndpointRepo) {}

  register(input: RegisterWebhookInput): Promise<WebhookEndpointRecord> {
    const secret = randomBytes(32).toString("hex");
    return this.repo.create({ ...input, secret });
  }

  list(tenantId: string, userId: string): Promise<WebhookEndpointRecord[]> {
    return this.repo.listEnabled(tenantId, userId);
  }

  update(tenantId: string, id: string, input: UpdateWebhookEndpointInput): Promise<WebhookEndpointRecord> {
    return this.repo.update(tenantId, id, input);
  }

  delete(tenantId: string, id: string): Promise<void> {
    return this.repo.delete(tenantId, id);
  }
}
