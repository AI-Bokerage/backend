import type { SlaPolicyRecord } from "../../../domain/ports/policy-repo.port.ts";
import type { SlaPolicyDto } from "./types.ts";

export function mapPolicyToDto(policy: SlaPolicyRecord): SlaPolicyDto {
  return {
    id: policy.id,
    tenantId: policy.tenantId,
    name: policy.name,
    entityType: policy.entityType,
    slaType: policy.slaType,
    durationMins: policy.durationMins,
    warningMins: policy.warningMins,
    isEnabled: policy.isEnabled,
    description: policy.description,
    createdBy: policy.createdBy,
    updatedBy: policy.updatedBy,
    createdAt: policy.createdAt.toISOString(),
    updatedAt: policy.updatedAt.toISOString(),
  };
}
