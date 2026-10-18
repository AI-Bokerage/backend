import type { ITenantBrandingRepo, BrandingRecord, UpsertBrandingInput, PatchBrandingInput } from "../../../domain/ports/tenant-branding.repository.port.ts";
import type { ITenantDomainRepo } from "../../../domain/ports/tenant-domain.repository.port.ts";
import { BrandingNotFoundError, BrandingValidationError } from "../../../common/errors.ts";

const HEX_COLOR = /^#[0-9a-fA-F]{6}$/;

const COLOR_FIELDS = ["primaryColor", "secondaryColor", "accentColor"] as const;

function assertValidColors(input: { primaryColor?: string | null; secondaryColor?: string | null; accentColor?: string | null }): void {
  for (const field of COLOR_FIELDS) {
    const value = input[field];
    if (value != null && !HEX_COLOR.test(value)) {
      throw new BrandingValidationError(`${field} must be a 6-digit hex color, got "${value}"`);
    }
  }
}

export class BrandingService {
  constructor(
    private readonly brandingRepo: ITenantBrandingRepo,
    private readonly domainRepo: ITenantDomainRepo,
  ) {}

  async get(tenantId: string): Promise<BrandingRecord> {
    const record = await this.brandingRepo.findByTenantId(tenantId);
    if (!record) throw new BrandingNotFoundError(tenantId);
    return record;
  }

  async upsert(input: UpsertBrandingInput): Promise<BrandingRecord> {
    assertValidColors(input);
    return this.brandingRepo.upsert(input);
  }

  async patch(tenantId: string, input: PatchBrandingInput): Promise<BrandingRecord> {
    assertValidColors(input);
    const record = await this.brandingRepo.patch(tenantId, input);
    if (!record) throw new BrandingNotFoundError(tenantId);
    return record;
  }

  async getManifest(opts: { tenantId?: string; domain?: string }): Promise<BrandingRecord> {
    let tenantId = opts.tenantId;
    if (!tenantId && opts.domain) {
      const domainRecord = await this.domainRepo.findByDomain(opts.domain);
      if (!domainRecord || (domainRecord.status !== "VERIFIED" && domainRecord.status !== "ACTIVE")) {
        throw new BrandingNotFoundError(opts.domain);
      }
      tenantId = domainRecord.tenantId;
    }
    if (!tenantId) throw new BrandingNotFoundError("unknown");
    const record = await this.brandingRepo.findByTenantId(tenantId);
    if (!record) throw new BrandingNotFoundError(tenantId);
    return record;
  }
}
