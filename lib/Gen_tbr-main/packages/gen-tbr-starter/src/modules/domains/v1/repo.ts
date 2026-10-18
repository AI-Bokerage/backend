import { Prisma, type PrismaClient } from "@prisma/client";
import type {
  ITenantDomainRepo,
  TenantDomainRecord,
  CreateDomainInput,
  UpdateDomainStatusFields,
} from "../../../domain/ports/tenant-domain.repository.port.ts";
import type { DomainStatus } from "../../../common/types.ts";
import { DomainAlreadyClaimedError } from "../../../common/errors.ts";

export class PrismaTenantDomainRepo implements ITenantDomainRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async create(input: CreateDomainInput): Promise<TenantDomainRecord> {
    try {
      const row = await this.prisma.tenantDomain.create({ data: input });
      return row as TenantDomainRecord;
    } catch (err) {
      if (err instanceof Prisma.PrismaClientKnownRequestError && err.code === "P2002") {
        throw new DomainAlreadyClaimedError(input.domain);
      }
      throw err;
    }
  }

  async findById(id: string): Promise<TenantDomainRecord | null> {
    const row = await this.prisma.tenantDomain.findUnique({ where: { id } });
    return row as TenantDomainRecord | null;
  }

  async findByDomain(domain: string): Promise<TenantDomainRecord | null> {
    const row = await this.prisma.tenantDomain.findUnique({ where: { domain } });
    return row as TenantDomainRecord | null;
  }

  async listByTenant(
    tenantId: string,
    opts?: { includeDetached?: boolean },
  ): Promise<TenantDomainRecord[]> {
    const rows = await this.prisma.tenantDomain.findMany({
      where: {
        tenantId,
        ...(opts?.includeDetached ? {} : { status: { not: "DETACHED" } }),
      },
      orderBy: { createdAt: "asc" },
    });
    return rows as TenantDomainRecord[];
  }

  async updateStatus(
    id: string,
    status: DomainStatus,
    fields?: UpdateDomainStatusFields,
  ): Promise<TenantDomainRecord> {
    const row = await this.prisma.tenantDomain.update({
      where: { id },
      data: { status, ...fields },
    });
    return row as TenantDomainRecord;
  }

  async setPrimary(tenantId: string, id: string): Promise<TenantDomainRecord> {
    return this.prisma.$transaction(async (tx) => {
      await tx.tenantDomain.updateMany({
        where: { tenantId, isPrimary: true },
        data: { isPrimary: false },
      });
      const row = await tx.tenantDomain.update({
        where: { id },
        data: { isPrimary: true },
      });
      return row as TenantDomainRecord;
    });
  }

  async touchLastChecked(id: string, at: Date): Promise<void> {
    await this.prisma.tenantDomain.update({ where: { id }, data: { lastCheckedAt: at } });
  }
}
