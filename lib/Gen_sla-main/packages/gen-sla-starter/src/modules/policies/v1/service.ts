import { SlaPolicyNotFoundError, SlaPolicyHasActiveInstancesError, SlaPolicyConflictError, SlaInvalidTimingError } from "../../../common/errors.ts";
import { logger } from "../../../common/logger.ts";
import { mapPolicyToDto } from "./mapper.ts";
import type { ISlaPolicyRepo } from "../../../domain/ports/policy-repo.port.ts";
import type { CreatePolicyInput, ListPoliciesQuery, UpdatePolicyInput } from "./schema.ts";
import type { SlaPolicyDto, PaginatedPolicies } from "./types.ts";

export class PolicyService {
  constructor(private readonly repo: ISlaPolicyRepo) {}

  async createPolicy(tenantId: string, input: CreatePolicyInput): Promise<SlaPolicyDto> {
    const existing = await this.repo.findByEntityAndType(tenantId, input.entityType, input.slaType);
    if (existing) {
      throw new SlaPolicyConflictError(input.entityType, input.slaType);
    }

    const policy = await this.repo.create({ tenantId, ...input });
    logger.info({ policyId: policy.id, tenantId, entityType: input.entityType }, "[policy] created");
    return mapPolicyToDto(policy);
  }

  async listPolicies(tenantId: string, query: ListPoliciesQuery): Promise<PaginatedPolicies> {
    const { data, total } = await this.repo.findAll(tenantId, {
      entityType: query.entityType,
      isEnabled: query.isEnabled,
      page: query.page,
      pageSize: query.pageSize,
    });

    return { data: data.map(mapPolicyToDto), total, page: query.page, pageSize: query.pageSize };
  }

  async getPolicy(tenantId: string, id: string): Promise<SlaPolicyDto> {
    const policy = await this.repo.findById(id, tenantId);
    if (!policy) throw new SlaPolicyNotFoundError(id);
    return mapPolicyToDto(policy);
  }

  async updatePolicy(tenantId: string, id: string, input: UpdatePolicyInput): Promise<SlaPolicyDto> {
    const existing = await this.repo.findById(id, tenantId);
    if (!existing) throw new SlaPolicyNotFoundError(id);

    const effectiveDuration = input.durationMins ?? existing.durationMins;
    const effectiveWarning = input.warningMins ?? existing.warningMins;

    // durationMins=0 is a sentinel for fixed-deadline policies (deadline supplied
    // at instance creation time, not computed from policy duration) — skip the
    // ratio check for these, only warningMins is meaningful.
    if (effectiveDuration > 0 && effectiveWarning >= effectiveDuration) {
      throw new SlaInvalidTimingError();
    }

    await this.repo.update(id, tenantId, input);
    const updated = await this.repo.findById(id, tenantId);
    if (!updated) throw new SlaPolicyNotFoundError(id);

    logger.info({ policyId: id, tenantId }, "[policy] updated");
    return mapPolicyToDto(updated);
  }

  async deletePolicy(tenantId: string, id: string): Promise<void> {
    const existing = await this.repo.findById(id, tenantId);
    if (!existing) throw new SlaPolicyNotFoundError(id);

    const activeCount = await this.repo.countActiveInstances(id, tenantId);
    if (activeCount > 0) {
      throw new SlaPolicyHasActiveInstancesError(id, activeCount);
    }

    const result = await this.repo.delete(id, tenantId);
    if (result.count === 0) throw new SlaPolicyNotFoundError(id);

    logger.info({ policyId: id, tenantId }, "[policy] deleted");
  }
}
