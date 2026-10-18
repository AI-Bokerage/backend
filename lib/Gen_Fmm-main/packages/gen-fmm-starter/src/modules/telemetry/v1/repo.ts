import type {
  ITelemetryRepo,
  UsageEventInput,
  UsageEventRecord,
  TelemetryQueryFilter,
} from "../../../domain/ports/telemetry.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

export class PrismaTelemetryRepo implements ITelemetryRepo {
  async insertMany(events: UsageEventInput[]): Promise<number> {
    if (events.length === 0) return 0;

    // The buffer is process-wide, not per-tenant, so a single flush can hold
    // events from several tenants. Group and insert per tenant so each
    // batch runs inside its own withTenant() transaction — RLS's WITH CHECK
    // requires app.tenant_id to match every inserted row.
    const byTenant = new Map<string, UsageEventInput[]>();
    for (const event of events) {
      const list = byTenant.get(event.tenantId) ?? [];
      list.push(event);
      byTenant.set(event.tenantId, list);
    }

    let total = 0;
    for (const [tenantId, tenantEvents] of byTenant) {
      const result = await withTenant(tenantId, (tx) =>
        tx.featureUsageEvent.createMany({
          data: tenantEvents.map((e) => ({
            tenantId: e.tenantId,
            flagKey: e.flagKey,
            enabled: e.enabled,
            reason: e.reason,
            planCode: e.planCode,
            occurredAt: e.occurredAt,
          })),
        }),
      );
      total += result.count;
    }
    return total;
  }

  async query(tenantId: string, filter: TelemetryQueryFilter): Promise<{ events: UsageEventRecord[]; total: number }> {
    return withTenant(tenantId, async (tx) => {
      const where = {
        tenantId,
        ...(filter.flagKey ? { flagKey: filter.flagKey } : {}),
        ...(filter.from || filter.to
          ? { occurredAt: { ...(filter.from ? { gte: filter.from } : {}), ...(filter.to ? { lte: filter.to } : {}) } }
          : {}),
      };
      const [rows, total] = await Promise.all([
        tx.featureUsageEvent.findMany({
          where,
          orderBy: { occurredAt: "desc" },
          skip: (filter.page - 1) * filter.pageSize,
          take: filter.pageSize,
        }),
        tx.featureUsageEvent.count({ where }),
      ]);
      return { events: rows, total };
    });
  }
}
