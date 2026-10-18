export interface WebhookEndpointRecord {
  id: string;
  tenantId: string;
  userId: string;
  url: string;
  secret: string;
  enabled: boolean;
  description: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface CreateWebhookEndpointInput {
  tenantId: string;
  userId: string;
  url: string;
  secret: string;
  description?: string;
}

export interface UpdateWebhookEndpointInput {
  url?: string;
  enabled?: boolean;
  description?: string;
}

export interface IWebhookEndpointRepo {
  create(input: CreateWebhookEndpointInput): Promise<WebhookEndpointRecord>;
  findById(tenantId: string, id: string): Promise<WebhookEndpointRecord | null>;
  listEnabled(tenantId: string, userId: string): Promise<WebhookEndpointRecord[]>;
  update(tenantId: string, id: string, input: UpdateWebhookEndpointInput): Promise<WebhookEndpointRecord>;
  delete(tenantId: string, id: string): Promise<void>;
}
