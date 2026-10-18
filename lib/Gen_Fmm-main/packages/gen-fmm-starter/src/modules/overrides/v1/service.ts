import type {
  ITenantOverrideRepo,
  UpsertOverrideInput,
  TenantOverrideRecord,
} from "../../../domain/ports/tenant-override.repository.port.ts";
import { BusinessRuleError } from "../../../common/errors.ts";
import type { EntitlementCache } from "../../entitlement/v1/cache.ts";

export class OverrideService {
  constructor(
    private readonly repo: ITenantOverrideRepo,
    private readonly cache: EntitlementCache,
    private readonly onFlagChanged: (flagKey: string, tenantId?: string) => void,
  ) {}

  async upsert(input: UpsertOverrideInput): Promise<TenantOverrideRecord> {
    if (input.expiresAt && input.expiresAt <= new Date()) {
      throw new BusinessRuleError("OVERRIDE_EXPIRY_IN_PAST", "expiresAt must be in the future");
    }
    const record = await this.repo.upsert(input);
    this.cache.invalidateTenantFlag(input.tenantId, input.flagKey);
    this.onFlagChanged(input.flagKey, input.tenantId);
    return record;
  }

  findOne(tenantId: string, flagKey: string): Promise<TenantOverrideRecord | null> {
    return this.repo.findOne(tenantId, flagKey);
  }

  listForTenant(tenantId: string): Promise<TenantOverrideRecord[]> {
    return this.repo.findAllForTenant(tenantId);
  }

  listAll(filter: { tenantId?: string; flagKey?: string }): Promise<TenantOverrideRecord[]> {
    return this.repo.listAll(filter);
  }

  async delete(tenantId: string, flagKey: string): Promise<boolean> {
    const deleted = await this.repo.delete(tenantId, flagKey);
    if (deleted) {
      this.cache.invalidateTenantFlag(tenantId, flagKey);
      this.onFlagChanged(flagKey, tenantId);
    }
    return deleted;
  }
}
