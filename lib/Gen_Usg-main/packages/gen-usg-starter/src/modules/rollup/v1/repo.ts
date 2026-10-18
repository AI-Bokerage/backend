import type { IMeterRepo, RollupPeriod, UsageSnapshotRecord } from "../../../domain/ports/meter.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

export class PrismaMeterRepo implements IMeterRepo {
  async insertSnapshot(tenantId: string, snapshotAt: Date, period: RollupPeriod, metrics: Record<string, number>): Promise<void> {
    await withTenant(tenantId, (tx) =>
      tx.usageSnapshot.create({ data: { tenantId, snapshotAt, period, metrics } }),
    );
  }

  async findTrend(tenantId: string, sinceDays: number): Promise<UsageSnapshotRecord[]> {
    const since = new Date(Date.now() - sinceDays * 24 * 60 * 60 * 1000);
    return withTenant(tenantId, (tx) =>
      tx.usageSnapshot.findMany({
        where: { tenantId, snapshotAt: { gte: since } },
        orderBy: { snapshotAt: "desc" },
      }),
    ) as unknown as UsageSnapshotRecord[];
  }

  async latestSnapshot(tenantId: string): Promise<UsageSnapshotRecord | null> {
    return withTenant(tenantId, (tx) =>
      tx.usageSnapshot.findFirst({
        where: { tenantId },
        orderBy: { snapshotAt: "desc" },
      }),
    ) as unknown as Promise<UsageSnapshotRecord | null>;
  }
}
