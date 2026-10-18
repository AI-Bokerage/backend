import type {
  IWebhookEndpointRepo,
  WebhookEndpointRecord,
  CreateWebhookEndpointInput,
  UpdateWebhookEndpointInput,
} from "../../../domain/ports/webhook-endpoint.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";
import { WebhookEndpointNotFoundError } from "../../../common/errors.ts";

export class PrismaWebhookEndpointRepo implements IWebhookEndpointRepo {
  async create(input: CreateWebhookEndpointInput): Promise<WebhookEndpointRecord> {
    return withTenant(input.tenantId, (tx) => tx.webhookEndpoint.create({ data: input }));
  }

  async findById(tenantId: string, id: string): Promise<WebhookEndpointRecord | null> {
    return withTenant(tenantId, (tx) => tx.webhookEndpoint.findFirst({ where: { id, tenantId } }));
  }

  async listEnabled(tenantId: string, userId: string): Promise<WebhookEndpointRecord[]> {
    return withTenant(tenantId, (tx) =>
      tx.webhookEndpoint.findMany({ where: { tenantId, userId, enabled: true } }),
    );
  }

  async update(tenantId: string, id: string, input: UpdateWebhookEndpointInput): Promise<WebhookEndpointRecord> {
    return withTenant(tenantId, async (tx) => {
      const result = await tx.webhookEndpoint.updateMany({ where: { id, tenantId }, data: input });
      if (result.count === 0) throw new WebhookEndpointNotFoundError(id);
      return tx.webhookEndpoint.findFirstOrThrow({ where: { id, tenantId } });
    });
  }

  async delete(tenantId: string, id: string): Promise<void> {
    await withTenant(tenantId, async (tx) => {
      const result = await tx.webhookEndpoint.deleteMany({ where: { id, tenantId } });
      if (result.count === 0) throw new WebhookEndpointNotFoundError(id);
    });
  }
}
