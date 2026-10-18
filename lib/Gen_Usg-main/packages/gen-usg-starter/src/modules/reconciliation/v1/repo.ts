import type { IReconciliationRepo, InsertReconciliationLogInput } from "../../../domain/ports/reconciliation.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

export class PrismaReconciliationRepo implements IReconciliationRepo {
  async insertLog(entry: InsertReconciliationLogInput): Promise<void> {
    await withTenant(entry.tenantId, (tx) =>
      tx.usageReconciliationLog.create({ data: entry }),
    );
  }
}
