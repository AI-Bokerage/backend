import type { PrismaClient, Prisma } from "../../../../__generated__/prisma/index.js";
import type {
  ISlaPolicyRepo,
  CreatePolicyParams,
  UpdatePolicyParams,
  ListPoliciesFilters,
  SlaPolicyRecord,
} from "../../../domain/ports/policy-repo.port.ts";

export class PrismaPolicyRepo implements ISlaPolicyRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async create(params: CreatePolicyParams): Promise<SlaPolicyRecord> {
    return this.prisma.slaPolicy.create({
      data: {
        tenantId: params.tenantId,
        name: params.name,
        entityType: params.entityType,
        slaType: params.slaType,
        durationMins: params.durationMins,
        warningMins: params.warningMins,
        isEnabled: params.isEnabled,
        description: params.description ?? null,
        createdBy: params.createdBy ?? null,
        updatedBy: params.createdBy ?? null,
      },
    });
  }

  async findById(id: string, tenantId: string): Promise<SlaPolicyRecord | null> {
    return this.prisma.slaPolicy.findFirst({ where: { id, tenantId } });
  }

  async findByEntityAndType(tenantId: string, entityType: string, slaType: string): Promise<SlaPolicyRecord | null> {
    return this.prisma.slaPolicy.findFirst({
      where: { tenantId, entityType, slaType, isEnabled: true },
    });
  }

  async findAll(
    tenantId: string,
    filters: ListPoliciesFilters,
  ): Promise<{ data: SlaPolicyRecord[]; total: number }> {
    const where: Prisma.SlaPolicyWhereInput = { tenantId };
    if (filters.entityType !== undefined) where.entityType = filters.entityType;
    if (filters.isEnabled !== undefined) where.isEnabled = filters.isEnabled;

    const [data, total] = await this.prisma.$transaction([
      this.prisma.slaPolicy.findMany({
        where,
        orderBy: [{ entityType: "asc" }, { slaType: "asc" }],
        skip: (filters.page - 1) * filters.pageSize,
        take: filters.pageSize,
      }),
      this.prisma.slaPolicy.count({ where }),
    ]);

    return { data, total };
  }

  async update(id: string, tenantId: string, params: UpdatePolicyParams): Promise<{ count: number }> {
    return this.prisma.slaPolicy.updateMany({
      where: { id, tenantId },
      data: {
        ...(params.name !== undefined && { name: params.name }),
        ...(params.durationMins !== undefined && { durationMins: params.durationMins }),
        ...(params.warningMins !== undefined && { warningMins: params.warningMins }),
        ...(params.isEnabled !== undefined && { isEnabled: params.isEnabled }),
        ...(params.description !== undefined && { description: params.description }),
        ...(params.updatedBy !== undefined && { updatedBy: params.updatedBy }),
        updatedAt: new Date(),
      },
    });
  }

  async countActiveInstances(policyId: string, tenantId: string): Promise<number> {
    return this.prisma.slaInstance.count({
      where: { policyId, tenantId, status: { in: ["ACTIVE", "WARNING"] } },
    });
  }

  async delete(id: string, tenantId: string): Promise<{ count: number }> {
    return this.prisma.slaPolicy.deleteMany({ where: { id, tenantId } });
  }
}
