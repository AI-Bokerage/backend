import type { SlaInstanceRecord } from "../../../domain/ports/instance-repo.port.ts";
import type { SlaInstanceDto } from "./types.ts";

export function mapInstanceToDto(instance: SlaInstanceRecord): SlaInstanceDto {
  return {
    id: instance.id,
    tenantId: instance.tenantId,
    policyId: instance.policyId,
    entityType: instance.entityType,
    entityId: instance.entityId,
    slaType: instance.slaType,
    status: instance.status,
    startedAt: instance.startedAt.toISOString(),
    dueAt: instance.dueAt.toISOString(),
    warningAt: instance.warningAt.toISOString(),
    breachedAt: instance.breachedAt?.toISOString() ?? null,
    resolvedAt: instance.resolvedAt?.toISOString() ?? null,
    metadata: instance.metadata ?? {},
  };
}
