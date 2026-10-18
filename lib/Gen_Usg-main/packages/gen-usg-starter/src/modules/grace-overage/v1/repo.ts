import type {
  IGraceOverageRepo,
  GraceOverageRecord,
} from "../../../domain/ports/grace-overage.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

const UNIQUE_VIOLATION = "P2002";

export class PrismaGraceOverageRepo implements IGraceOverageRepo {
  async findOpen(tenantId: string, metric: string): Promise<GraceOverageRecord | null> {
    return withTenant(tenantId, (tx) =>
      tx.usageGraceOverage.findFirst({ where: { tenantId, metric, status: "OPEN" } }),
    );
  }

  async open(tenantId: string, metric: string, graceStartedAt: Date, graceExpiresAt: Date): Promise<GraceOverageRecord> {
    try {
      return await withTenant(tenantId, (tx) =>
        tx.usageGraceOverage.create({
          data: { tenantId, metric, graceStartedAt, graceExpiresAt, overageCount: 1, status: "OPEN" },
        }),
      );
    } catch (err) {
      // A concurrent caller won the @@unique([tenantId, metric, status]) race —
      // re-read the row it created instead of throwing.
      if ((err as { code?: string }).code === UNIQUE_VIOLATION) {
        const existing = await this.findOpen(tenantId, metric);
        if (existing) return existing;
      }
      throw err;
    }
  }

  async bump(tenantId: string, id: string): Promise<GraceOverageRecord> {
    return withTenant(tenantId, (tx) =>
      tx.usageGraceOverage.update({
        where: { id },
        data: { overageCount: { increment: 1 } },
      }),
    );
  }

  async listOpenForTenant(tenantId: string): Promise<GraceOverageRecord[]> {
    return withTenant(tenantId, (tx) =>
      tx.usageGraceOverage.findMany({ where: { tenantId, status: "OPEN" } }),
    );
  }

  async listExpiredOpen(tenantId: string, now: Date): Promise<GraceOverageRecord[]> {
    return withTenant(tenantId, (tx) =>
      tx.usageGraceOverage.findMany({
        where: { tenantId, status: "OPEN", graceExpiresAt: { lte: now } },
      }),
    );
  }

  async close(tenantId: string, id: string): Promise<void> {
    await withTenant(tenantId, (tx) =>
      tx.usageGraceOverage.update({
        where: { id },
        data: { status: "CLOSED" },
      }),
    );
  }
}
