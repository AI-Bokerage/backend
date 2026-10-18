import type {
  ITenantPreferenceRepo,
  PreferenceRecord,
  UpsertPreferenceInput,
} from "../../../domain/ports/tenant-preference.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

export class PrismaTenantPreferenceRepo implements ITenantPreferenceRepo {
  async findAll(tenantId: string, userId: string): Promise<PreferenceRecord[]> {
    return withTenant(tenantId, (tx) =>
      tx.notificationPreference.findMany({ where: { tenantId, userId } }),
    );
  }

  async findByEventType(tenantId: string, userId: string, eventType: string): Promise<PreferenceRecord[]> {
    return withTenant(tenantId, (tx) =>
      tx.notificationPreference.findMany({ where: { tenantId, userId, eventType } }),
    );
  }

  async upsert(input: UpsertPreferenceInput): Promise<PreferenceRecord> {
    return withTenant(input.tenantId, (tx) =>
      tx.notificationPreference.upsert({
        where: {
          tenantId_userId_eventType_channel: {
            tenantId: input.tenantId,
            userId: input.userId,
            eventType: input.eventType,
            channel: input.channel,
          },
        },
        create: input,
        update: { enabled: input.enabled, digestMode: input.digestMode },
      }),
    );
  }
}
