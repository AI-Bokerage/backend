import { randomUUID } from "node:crypto";
import type { TntClientPort } from "../../../domain/ports/tnt-client.port.ts";
import { TntHttpError } from "../../../infra/external/http-tnt-client.ts";
import type { EventPublisher, EventEnvelope } from "../../../domain/ports/event-publisher.port.ts";
import { PLATFORM_AUDIT_EXCHANGE, TENANT_ROUTING_KEYS } from "../../../config/constants.ts";
import { TenantSlugTakenError, TenantNotFoundError, TenantTransitionConflictError, TntClientError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import type { CreateTenantInput, TenantDto, CreateTenantResultDto } from "./types.ts";

export class TenantsService {
  constructor(
    private readonly tntClient: TntClientPort,
    private readonly eventPublisher: EventPublisher,
  ) {}

  private mapTntError(
    err: unknown,
    context: { op: "create" | "get" | "suspend" | "reactivate"; id?: string; slug?: string; transition?: string },
  ): never {
    if (err instanceof TntHttpError) {
      if (err.status === 404 && context.op !== "create") throw new TenantNotFoundError(context.id ?? context.slug ?? "unknown");
      if (err.status === 409 && context.op === "create") throw new TenantSlugTakenError(context.slug ?? "unknown");
      if (err.status === 409 && context.transition) throw new TenantTransitionConflictError(context.id!, context.transition);
      throw new TntClientError(err.message);
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
      logger.warn({ err, routingKey, tenantId }, "[Tenants] Audit event publish failed — continuing");
    }
  }

  async create(input: CreateTenantInput): Promise<CreateTenantResultDto> {
    const primaryOwnerUserId = randomUUID();

    let tenant;
    try {
      tenant = await this.tntClient.createTenant({
        name: input.name,
        slug: input.slug,
        region: input.region,
        primaryOwnerUserId,
        idempotencyKey: input.idempotencyKey,
      });
    } catch (err) {
      this.mapTntError(err, { op: "create", slug: input.slug });
    }

    await this.publishSafely(TENANT_ROUTING_KEYS.CREATED, tenant.id, {
      tenant_slug: tenant.slug,
      tenant_name: tenant.name,
      region: tenant.region,
      owner_email: input.ownerEmail,
    });

    return {
      ...tenant,
      ownerEmail: input.ownerEmail,
      ownerFirstName: input.ownerFirstName ?? null,
      ownerLastName: input.ownerLastName ?? null,
    };
  }

  async getById(id: string): Promise<TenantDto> {
    let tenant;
    try {
      tenant = await this.tntClient.getTenant(id);
    } catch (err) {
      this.mapTntError(err, { op: "get", id });
    }
    if (!tenant) throw new TenantNotFoundError(id);
    return tenant;
  }

  async getBySlug(slug: string): Promise<TenantDto> {
    let tenant;
    try {
      tenant = await this.tntClient.getTenantBySlug(slug);
    } catch (err) {
      this.mapTntError(err, { op: "get", slug });
    }
    if (!tenant) throw new TenantNotFoundError(slug);
    return tenant;
  }

  async suspend(id: string, reason: string): Promise<TenantDto> {
    let tenant;
    try {
      tenant = await this.tntClient.suspendTenant(id);
    } catch (err) {
      this.mapTntError(err, { op: "suspend", id, transition: "suspend" });
    }

    await this.publishSafely(TENANT_ROUTING_KEYS.SUSPENDED, id, { reason });
    return tenant;
  }

  async reactivate(id: string, note?: string): Promise<TenantDto> {
    let tenant;
    try {
      tenant = await this.tntClient.reactivateTenant(id);
    } catch (err) {
      this.mapTntError(err, { op: "reactivate", id, transition: "reactivate" });
    }

    await this.publishSafely(TENANT_ROUTING_KEYS.REACTIVATED, id, { note: note ?? null });
    return tenant;
  }
}
