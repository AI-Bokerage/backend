import type { PrismaClient, Prisma } from "../../../../__generated__/prisma/index.js";
import type {
  ISlaInstanceRepo,
  CreateInstanceParams,
  ListInstancesFilters,
  SlaInstanceRecord,
} from "../../../domain/ports/instance-repo.port.ts";

export class PrismaInstanceRepo implements ISlaInstanceRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async create(params: CreateInstanceParams): Promise<SlaInstanceRecord> {
    return this.prisma.slaInstance.create({
      data: {
        tenantId: params.tenantId,
        policyId: params.policyId,
        entityType: params.entityType,
        entityId: params.entityId,
        slaType: params.slaType,
        status: "ACTIVE",
        startedAt: params.startedAt,
        dueAt: params.dueAt,
        warningAt: params.warningAt,
        metadata: params.metadata as Prisma.InputJsonValue,
      },
    }) as unknown as Promise<SlaInstanceRecord>;
  }

  async findById(id: string, tenantId: string): Promise<SlaInstanceRecord | null> {
    return this.prisma.slaInstance.findFirst({ where: { id, tenantId } }) as unknown as Promise<SlaInstanceRecord | null>;
  }

  async findByEntityAndType(tenantId: string, entityId: string, slaType: string): Promise<SlaInstanceRecord | null> {
    return this.prisma.slaInstance.findFirst({
      where: { tenantId, entityId, slaType },
    }) as unknown as Promise<SlaInstanceRecord | null>;
  }

  async findAll(
    tenantId: string,
    filters: ListInstancesFilters,
  ): Promise<{ data: SlaInstanceRecord[]; total: number }> {
    const where: Prisma.SlaInstanceWhereInput = { tenantId };
    if (filters.entityType !== undefined) where.entityType = filters.entityType;
    if (filters.entityId !== undefined) where.entityId = filters.entityId;
    if (filters.status !== undefined) where.status = filters.status;

    const [data, total] = await this.prisma.$transaction([
      this.prisma.slaInstance.findMany({
        where,
        orderBy: { startedAt: "desc" },
        skip: (filters.page - 1) * filters.pageSize,
        take: filters.pageSize,
      }),
      this.prisma.slaInstance.count({ where }),
    ]);

    return { data: data as unknown as SlaInstanceRecord[], total };
  }

  async updateStatus(
    id: string,
    tenantId: string,
    status: string,
    extra?: { breachedAt?: Date; resolvedAt?: Date },
  ): Promise<SlaInstanceRecord> {
    return this.prisma.slaInstance.update({
      where: { id, tenantId },
      data: {
        status,
        ...(extra?.breachedAt && { breachedAt: extra.breachedAt }),
        ...(extra?.resolvedAt && { resolvedAt: extra.resolvedAt }),
      },
    }) as unknown as Promise<SlaInstanceRecord>;
  }

  async appendHistory(
    tenantId: string,
    instanceId: string,
    fromStatus: string | null,
    toStatus: string,
    note?: string,
  ): Promise<void> {
    await this.prisma.slaHistory.create({
      data: { tenantId, instanceId, fromStatus, toStatus, note },
    });
  }

  async findActiveInstancesDue(batchSize: number): Promise<SlaInstanceRecord[]> {
    const now = new Date();
    return this.prisma.slaInstance.findMany({
      where: { status: { in: ["ACTIVE", "WARNING"] }, dueAt: { lte: now } },
      take: batchSize,
      orderBy: { dueAt: "asc" },
    }) as unknown as Promise<SlaInstanceRecord[]>;
  }

  async findActiveInstancesNearWarning(batchSize: number): Promise<SlaInstanceRecord[]> {
    const now = new Date();
    return this.prisma.slaInstance.findMany({
      where: { status: "ACTIVE", warningAt: { lte: now } },
      take: batchSize,
      orderBy: { warningAt: "asc" },
    }) as unknown as Promise<SlaInstanceRecord[]>;
  }

  async cancelByEntityId(tenantId: string, entityId: string, slaTypes: string[]): Promise<{ count: number }> {
    return this.prisma.slaInstance.updateMany({
      where: { tenantId, entityId, slaType: { in: slaTypes }, status: { in: ["ACTIVE", "WARNING"] } },
      data: { status: "CANCELLED" },
    });
  }

  async resolveByEntityId(tenantId: string, entityId: string, slaTypes: string[]): Promise<{ count: number }> {
    return this.prisma.slaInstance.updateMany({
      where: { tenantId, entityId, slaType: { in: slaTypes }, status: { in: ["ACTIVE", "WARNING"] } },
      data: { status: "RESOLVED", resolvedAt: new Date() },
    });
  }

  async findActiveByEntityId(tenantId: string, entityId: string): Promise<SlaInstanceRecord[]> {
    return this.prisma.slaInstance.findMany({
      where: { tenantId, entityId, status: "ACTIVE" },
    }) as unknown as Promise<SlaInstanceRecord[]>;
  }

  async createEscalation(tenantId: string, instanceId: string, level: number, eventType: string): Promise<void> {
    await this.prisma.slaEscalation.create({
      data: { tenantId, instanceId, level, eventType, notified: false },
    });
  }
}
