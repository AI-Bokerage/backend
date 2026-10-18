import type { FmmClientPort, FmmFlagResponse, FmmOverrideResponse } from "../../../domain/ports/fmm-client.port.ts";
import { FmmHttpError } from "../../../infra/external/http-fmm-client.ts";
import type { EventPublisher, EventEnvelope } from "../../../domain/ports/event-publisher.port.ts";
import { PLATFORM_AUDIT_EXCHANGE, FLAG_ROUTING_KEYS } from "../../../config/constants.ts";
import { FlagNotFoundError, FmmClientError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import type { UpdateFlagInput, SetOverrideInput } from "./types.ts";

// Flag catalog updates aren't tenant-scoped, but EventEnvelope.tenant_id is
// required — this sentinel marks platform-level (non-tenant) audit events.
const PLATFORM_TENANT_ID = "platform";

type FmmErrorOp = "list" | "updateFlag" | "setOverride" | "listOverrides" | "clearOverride";

export class FeatureFlagsService {
  constructor(
    private readonly fmmClient: FmmClientPort,
    private readonly eventPublisher: EventPublisher,
  ) {}

  // Gated on which operation ran, not on which context fields happen to be
  // populated — the tenants module's mapTntError once mapped a create() 404
  // to TENANT_NOT_FOUND just because `context.slug` was set, which was wrong
  // for that operation. Same class of bug, avoided here by keying strictly on `op`.
  private mapFmmError(err: unknown, context: { op: FmmErrorOp; key?: string; flagKey?: string }): never {
    if (err instanceof FmmHttpError) {
      if (err.status === 404 && context.op === "updateFlag") throw new FlagNotFoundError(context.key ?? "unknown");
      // Gen_FMM's POST /overrides returns 422 REFERENCED_RECORD_NOT_FOUND when
      // flagKey isn't in its catalog — the likeliest real-world caller error
      // (typo'd flag key). Surface it as FLAG_NOT_FOUND instead of an opaque 502.
      if (err.status === 422 && context.op === "setOverride") throw new FlagNotFoundError(context.flagKey ?? "unknown");
      throw new FmmClientError(err.message);
    }
    throw err instanceof Error ? err : new Error(String(err));
  }

  private async publishSafely(routingKey: string, tenantId: string, data: Record<string, unknown>): Promise<void> {
    const envelope: EventEnvelope = {
      event_type: routingKey,
      occurred_at: new Date().toISOString(),
      tenant_id: tenantId,
      data,
    };
    try {
      await this.eventPublisher.publish(PLATFORM_AUDIT_EXCHANGE, routingKey, envelope);
    } catch (err) {
      logger.warn({ err, routingKey, tenantId }, "[FeatureFlags] Audit event publish failed — continuing");
    }
  }

  async listFlags(): Promise<FmmFlagResponse[]> {
    let flags: FmmFlagResponse[];
    try {
      flags = await this.fmmClient.listFlags();
    } catch (err) {
      this.mapFmmError(err, { op: "list" });
    }
    return flags;
  }

  async updateFlag(key: string, input: UpdateFlagInput): Promise<FmmFlagResponse> {
    const { reason, ...patch } = input;
    let flag: FmmFlagResponse;
    try {
      flag = await this.fmmClient.updateFlag(key, patch);
    } catch (err) {
      this.mapFmmError(err, { op: "updateFlag", key });
    }
    await this.publishSafely(FLAG_ROUTING_KEYS.UPDATED, PLATFORM_TENANT_ID, { flag_key: key, ...patch, reason });
    return flag;
  }

  async setOverride(tenantId: string, flagKey: string, input: SetOverrideInput): Promise<FmmOverrideResponse> {
    let override: FmmOverrideResponse;
    try {
      override = await this.fmmClient.setOverride({
        tenantId,
        flagKey,
        enabled: input.enabled,
        config: input.config,
        expiresAt: input.expiresAt,
        reason: input.reason,
      });
    } catch (err) {
      this.mapFmmError(err, { op: "setOverride", flagKey });
    }
    await this.publishSafely(FLAG_ROUTING_KEYS.OVERRIDE_SET, tenantId, { flag_key: flagKey, enabled: input.enabled, reason: input.reason });
    return override;
  }

  async listOverridesForTenant(tenantId: string): Promise<FmmOverrideResponse[]> {
    let overrides: FmmOverrideResponse[];
    try {
      overrides = await this.fmmClient.listOverridesForTenant(tenantId);
    } catch (err) {
      this.mapFmmError(err, { op: "listOverrides" });
    }
    return overrides;
  }

  async clearOverride(tenantId: string, flagKey: string): Promise<void> {
    try {
      await this.fmmClient.clearOverride(tenantId, flagKey);
    } catch (err) {
      this.mapFmmError(err, { op: "clearOverride" });
    }
    await this.publishSafely(FLAG_ROUTING_KEYS.OVERRIDE_CLEARED, tenantId, { flag_key: flagKey });
  }
}
