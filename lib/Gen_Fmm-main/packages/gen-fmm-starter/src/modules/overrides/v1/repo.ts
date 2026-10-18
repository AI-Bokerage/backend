import type { Prisma } from "@prisma/client";
import type {
  ITenantOverrideRepo,
  TenantOverrideRecord,
  UpsertOverrideInput,
} from "../../../domain/ports/tenant-override.repository.port.ts";
import { withTenant } from "../../../infra/persistence/with-tenant.ts";

function toRecord(row: {
  tenantId: string; flagKey: string; enabled: boolean; config: unknown;
  reason: string | null; expiresAt: Date | null; createdBy: string | null;
}): TenantOverrideRecord {
  return {
    tenantId: row.tenantId,
    flagKey: row.flagKey,
    enabled: row.enabled,
    config: row.config as Record<string, unknown>,
    reason: row.reason,
    expiresAt: row.expiresAt,
    createdBy: row.createdBy,
  };
}

export class PrismaTenantOverrideRepo implements ITenantOverrideRepo {
  async upsert(input: UpsertOverrideInput): Promise<TenantOverrideRecord> {
    return withTenant(input.tenantId, async (tx) => {
      const row = await tx.tenantFeatureFlag.upsert({
        where: { tenantId_flagKey: { tenantId: input.tenantId, flagKey: input.flagKey } },
        create: {
          tenantId: input.tenantId,
          flagKey: input.flagKey,
          enabled: input.enabled,
          config: (input.config ?? {}) as Prisma.InputJsonValue,
          reason: input.reason ?? null,
          expiresAt: input.expiresAt ?? null,
          createdBy: input.createdBy ?? null,
        },
        update: {
          enabled: input.enabled,
          config: (input.config ?? {}) as Prisma.InputJsonValue,
          reason: input.reason ?? null,
          expiresAt: input.expiresAt ?? null,
          createdBy: input.createdBy ?? null,
        },
      });
      return toRecord(row);
    });
  }

  async findOne(tenantId: string, flagKey: string): Promise<TenantOverrideRecord | null> {
    return withTenant(tenantId, async (tx) => {
      const row = await tx.tenantFeatureFlag.findUnique({ where: { tenantId_flagKey: { tenantId, flagKey } } });
      return row ? toRecord(row) : null;
    });
  }

  async findAllForTenant(tenantId: string): Promise<TenantOverrideRecord[]> {
    return withTenant(tenantId, async (tx) => {
      const rows = await tx.tenantFeatureFlag.findMany({ where: { tenantId } });
      return rows.map(toRecord);
    });
  }

  async listAll(filter: { tenantId?: string; flagKey?: string }): Promise<TenantOverrideRecord[]> {
    // Cross-tenant admin listing has no single tenant to scope withTenant()
    // to; it runs on a separate, elevated connection (getAdminPrismaClient)
    // specifically configured to bypass RLS for this read, not the same
    // restricted genfmm_app connection every other method uses. This method
    // exists for a host-fronted admin surface — the host is responsible for
    // gating who may call it, same as every other route in this library.
    const rows = await this.rawFindMany(filter);
    return rows.map(toRecord);
  }

  private async rawFindMany(filter: { tenantId?: string; flagKey?: string }) {
    const { getAdminPrismaClient } = await import("../../../infra/persistence/prisma-client.ts");
    return getAdminPrismaClient().tenantFeatureFlag.findMany({
      where: {
        ...(filter.tenantId ? { tenantId: filter.tenantId } : {}),
        ...(filter.flagKey ? { flagKey: filter.flagKey } : {}),
      },
    });
  }

  async delete(tenantId: string, flagKey: string): Promise<boolean> {
    return withTenant(tenantId, async (tx) => {
      const result = await tx.tenantFeatureFlag.deleteMany({ where: { tenantId, flagKey } });
      return result.count > 0;
    });
  }
}
