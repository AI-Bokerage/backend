import type { PrismaClient, Prisma } from "@prisma/client";
import type {
  ITenantBrandingRepo,
  BrandingRecord,
  UpsertBrandingInput,
  PatchBrandingInput,
} from "../../../domain/ports/tenant-branding.repository.port.ts";

export class PrismaTenantBrandingRepo implements ITenantBrandingRepo {
  constructor(private readonly prisma: PrismaClient) {}

  async findByTenantId(tenantId: string): Promise<BrandingRecord | null> {
    const row = await this.prisma.tenantBranding.findUnique({ where: { tenantId } });
    return row as BrandingRecord | null;
  }

  async upsert(input: UpsertBrandingInput): Promise<BrandingRecord> {
    const { tenantId, ...rest } = input;
    // ponytail: the domain port intentionally types rawMeta as `unknown`;
    // Prisma wants its own Json input type, so bridge it here at the adapter edge.
    const row = await this.prisma.tenantBranding.upsert({
      where: { tenantId },
      create: { tenantId, ...rest } as Prisma.TenantBrandingUncheckedCreateInput,
      update: rest as Prisma.TenantBrandingUncheckedUpdateInput,
    });
    return row as BrandingRecord;
  }

  async patch(tenantId: string, input: PatchBrandingInput): Promise<BrandingRecord | null> {
    const exists = await this.prisma.tenantBranding.findUnique({ where: { tenantId } });
    if (!exists) return null;
    const row = await this.prisma.tenantBranding.update({
      where: { tenantId },
      data: input as Prisma.TenantBrandingUncheckedUpdateInput,
    });
    return row as BrandingRecord;
  }
}
