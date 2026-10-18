import { randomUUID } from "node:crypto";
import { SlaInstanceNotFoundError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import { SLA_EXCHANGE, SLA_ROUTING_KEYS } from "../../../config/constants.ts";
import { mapInstanceToDto } from "./mapper.ts";
import type { ISlaInstanceRepo, SlaInstanceRecord } from "../../../domain/ports/instance-repo.port.ts";
import type { ISlaPolicyRepo } from "../../../domain/ports/policy-repo.port.ts";
import type { IOutboxWriter } from "../../../domain/ports/outbox-writer.port.ts";
import type { CreateInstanceParams as CreateInstanceRepoParams } from "../../../domain/ports/instance-repo.port.ts";
import type { ListInstancesQuery } from "./schema.ts";
import type { SlaInstanceDto, PaginatedInstances } from "./types.ts";

function buildEventPayload(eventType: string, tenantId: string, data: Record<string, unknown>) {
  return {
    event_id: randomUUID(),
    event_type: eventType,
    event_version: 1,
    occurred_at: new Date().toISOString(),
    producer: { service: "gen-sla-starter", version: "dev" },
    tenant_id: tenantId,
    data,
  };
}

export class InstanceService {
  constructor(
    private readonly instanceRepo: ISlaInstanceRepo,
    private readonly policyRepo: ISlaPolicyRepo,
    private readonly outbox: IOutboxWriter,
  ) {}

  async createInstance(params: CreateInstanceRepoParams): Promise<SlaInstanceDto> {
    // The DB's @@unique([tenantId, entityId, slaType]) means at most one row can ever
    // exist for this key regardless of status — a second create() attempt would 500 on
    // P2002, so always short-circuit to the existing row rather than pretending a new
    // one could be created once the old one is RESOLVED/CANCELLED/BREACHED.
    const existing = await this.instanceRepo.findByEntityAndType(params.tenantId, params.entityId, params.slaType);
    if (existing !== null) {
      logger.info({ instanceId: existing.id, entityId: params.entityId, slaType: params.slaType }, "[instance] instance already exists — skipping");
      return mapInstanceToDto(existing);
    }

    const instance = await this.instanceRepo.create(params);
    await this.instanceRepo.appendHistory(params.tenantId, instance.id, null, "ACTIVE", "SLA instance created");

    await this.outbox.enqueue({
      tenantId: params.tenantId,
      eventType: SLA_ROUTING_KEYS.INSTANCE_CREATED,
      exchange: SLA_EXCHANGE,
      routingKey: SLA_ROUTING_KEYS.INSTANCE_CREATED,
      payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_CREATED, instance.tenantId, {
        instanceId: instance.id,
        entityType: instance.entityType,
        entityId: instance.entityId,
        slaType: instance.slaType,
        dueAt: instance.dueAt.toISOString(),
        warningAt: instance.warningAt.toISOString(),
      }),
    });

    logger.info({ instanceId: instance.id, entityId: params.entityId, slaType: params.slaType }, "[instance] created");
    return mapInstanceToDto(instance);
  }

  async listInstances(tenantId: string, query: ListInstancesQuery): Promise<PaginatedInstances> {
    const { data, total } = await this.instanceRepo.findAll(tenantId, {
      entityType: query.entityType,
      entityId: query.entityId,
      status: query.status,
      page: query.page,
      pageSize: query.pageSize,
    });
    return { data: data.map(mapInstanceToDto), total, page: query.page, pageSize: query.pageSize };
  }

  async getInstance(tenantId: string, id: string): Promise<SlaInstanceDto> {
    const instance = await this.instanceRepo.findById(id, tenantId);
    if (!instance) throw new SlaInstanceNotFoundError(id);
    return mapInstanceToDto(instance);
  }

  async transitionToWarning(instanceId: string, tenantId: string): Promise<void> {
    const instance = await this.instanceRepo.findById(instanceId, tenantId);
    if (!instance || instance.status !== "ACTIVE") return;

    await this.instanceRepo.updateStatus(instanceId, tenantId, "WARNING");
    await this.instanceRepo.appendHistory(tenantId, instanceId, "ACTIVE", "WARNING", "SLA warning threshold reached");

    await this.outbox.enqueue({
      tenantId,
      eventType: SLA_ROUTING_KEYS.INSTANCE_WARNING,
      exchange: SLA_EXCHANGE,
      routingKey: SLA_ROUTING_KEYS.INSTANCE_WARNING,
      payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_WARNING, tenantId, {
        instanceId, entityType: instance.entityType, entityId: instance.entityId, slaType: instance.slaType, dueAt: instance.dueAt.toISOString(),
      }),
    });

    logger.info({ instanceId, tenantId }, "[instance] transitioned to WARNING");
  }

  async transitionToBreached(instanceId: string, tenantId: string): Promise<void> {
    const instance = await this.instanceRepo.findById(instanceId, tenantId);
    if (!instance || !["ACTIVE", "WARNING"].includes(instance.status)) return;

    await this.instanceRepo.updateStatus(instanceId, tenantId, "BREACHED", { breachedAt: new Date() });
    await this.instanceRepo.appendHistory(tenantId, instanceId, instance.status, "BREACHED", "SLA due time exceeded");

    await this.outbox.enqueue({
      tenantId,
      eventType: SLA_ROUTING_KEYS.INSTANCE_BREACHED,
      exchange: SLA_EXCHANGE,
      routingKey: SLA_ROUTING_KEYS.INSTANCE_BREACHED,
      payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_BREACHED, tenantId, {
        instanceId, entityType: instance.entityType, entityId: instance.entityId, slaType: instance.slaType, breachedAt: new Date().toISOString(),
      }),
    });

    logger.warn({ instanceId, tenantId }, "[instance] BREACHED");
  }

  async resolveInstances(tenantId: string, entityId: string, slaTypes: string[]): Promise<void> {
    await this.instanceRepo.resolveByEntityId(tenantId, entityId, slaTypes);

    await this.outbox.enqueue({
      tenantId,
      eventType: SLA_ROUTING_KEYS.INSTANCE_RESOLVED,
      exchange: SLA_EXCHANGE,
      routingKey: SLA_ROUTING_KEYS.INSTANCE_RESOLVED,
      payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_RESOLVED, tenantId, { entityId, slaTypes, resolvedAt: new Date().toISOString() }),
    });

    logger.info({ tenantId, entityId, slaTypes }, "[instance] resolved");
  }

  async escalateInstances(tenantId: string, entityId: string, level: number): Promise<void> {
    const activeInstances = await this.instanceRepo.findActiveByEntityId(tenantId, entityId);
    if (activeInstances.length === 0) {
      logger.debug({ tenantId, entityId }, "[instance] escalation received but no ACTIVE instances found — skipping");
      return;
    }

    for (const instance of activeInstances) {
      await this.instanceRepo.updateStatus(instance.id, tenantId, "WARNING");
      await this.instanceRepo.appendHistory(tenantId, instance.id, "ACTIVE", "WARNING", `Escalated (level ${level})`);
      await this.instanceRepo.createEscalation(tenantId, instance.id, level, "instance.escalated");

      await this.outbox.enqueue({
        tenantId,
        eventType: SLA_ROUTING_KEYS.INSTANCE_WARNING,
        exchange: SLA_EXCHANGE,
        routingKey: SLA_ROUTING_KEYS.INSTANCE_WARNING,
        payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_WARNING, tenantId, {
          instanceId: instance.id, entityType: instance.entityType, entityId: instance.entityId, slaType: instance.slaType,
          dueAt: instance.dueAt.toISOString(), escalation: { level },
        }),
      });
    }

    logger.info({ tenantId, entityId, level, count: activeInstances.length }, "[instance] escalated — forced to WARNING");
  }

  async cancelInstances(tenantId: string, entityId: string, slaTypes: string[]): Promise<void> {
    await this.instanceRepo.cancelByEntityId(tenantId, entityId, slaTypes);

    await this.outbox.enqueue({
      tenantId,
      eventType: SLA_ROUTING_KEYS.INSTANCE_CANCELLED,
      exchange: SLA_EXCHANGE,
      routingKey: SLA_ROUTING_KEYS.INSTANCE_CANCELLED,
      payload: buildEventPayload(SLA_ROUTING_KEYS.INSTANCE_CANCELLED, tenantId, { entityId, slaTypes, cancelledAt: new Date().toISOString() }),
    });

    logger.info({ tenantId, entityId, slaTypes }, "[instance] cancelled");
  }

  async createFromPolicy(
    tenantId: string,
    entityType: string,
    entityId: string,
    slaType: string,
    metadata: Record<string, unknown>,
    startedAt: Date,
  ): Promise<SlaInstanceDto | null> {
    const policy = await this.policyRepo.findByEntityAndType(tenantId, entityType, slaType);
    if (!policy || !policy.isEnabled) return null;

    const dueAt = new Date(startedAt.getTime() + policy.durationMins * 60_000);
    const warningAt = new Date(startedAt.getTime() + policy.warningMins * 60_000);

    return this.createInstance({ tenantId, policyId: policy.id, entityType, entityId, slaType, startedAt, dueAt, warningAt, metadata });
  }

  async createWithFixedDeadline(
    tenantId: string,
    entityType: string,
    entityId: string,
    slaType: string,
    dueAt: Date,
    metadata: Record<string, unknown>,
  ): Promise<SlaInstanceDto | null> {
    const policy = await this.policyRepo.findByEntityAndType(tenantId, entityType, slaType);
    if (!policy || !policy.isEnabled) return null;

    const warningAt = new Date(dueAt.getTime() - policy.warningMins * 60_000);
    return this.createInstance({ tenantId, policyId: policy.id, entityType, entityId, slaType, startedAt: new Date(), dueAt, warningAt, metadata });
  }
}
