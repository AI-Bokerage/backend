import * as crypto from "node:crypto";
import type { ITenantDomainRepo, TenantDomainRecord, CreateDomainInput } from "../../../domain/ports/tenant-domain.repository.port.ts";
import type { IDnsVerifier } from "../../../domain/ports/dns-verifier.port.ts";
import { DomainNotFoundError, DomainVerificationFailedError, InvalidDomainStatusTransitionError } from "../../../common/errors.ts";
import type { DomainVerificationMethod } from "../../../common/types.ts";

function normalizeDomain(raw: string): string {
  return raw.trim().toLowerCase().replace(/\.$/, "");
}

function txtRecordName(domain: string): string {
  return `_gen-tbr-verify.${domain}`;
}

function cnameRecordName(domain: string): string {
  return `_gen-tbr.${domain}`;
}

export class DomainService {
  constructor(
    private readonly domainRepo: ITenantDomainRepo,
    private readonly dnsVerifier: IDnsVerifier,
  ) {}

  async claim(input: { tenantId: string; domain: string; verificationMethod?: DomainVerificationMethod }): Promise<TenantDomainRecord> {
    const domain = normalizeDomain(input.domain);
    const verificationToken = crypto.randomBytes(32).toString("hex");
    const create: CreateDomainInput = {
      tenantId: input.tenantId,
      domain,
      verificationToken,
      verificationMethod: input.verificationMethod ?? "TXT",
    };
    return this.domainRepo.create(create);
  }

  async list(tenantId: string, opts?: { includeDetached?: boolean }): Promise<TenantDomainRecord[]> {
    return this.domainRepo.listByTenant(tenantId, opts);
  }

  private async findOrThrow(id: string): Promise<TenantDomainRecord> {
    const record = await this.domainRepo.findById(id);
    if (!record) throw new DomainNotFoundError(id);
    return record;
  }

  async verify(tenantId: string, id: string): Promise<TenantDomainRecord> {
    const domainRecord = await this.findOrThrow(id);
    if (domainRecord.tenantId !== tenantId) throw new DomainNotFoundError(id);
    if (domainRecord.status !== "PENDING") {
      throw new InvalidDomainStatusTransitionError(domainRecord.status, "VERIFIED");
    }
    const now = new Date();

    let matched = false;
    if (domainRecord.verificationMethod === "TXT") {
      const records = await this.dnsVerifier.resolveTxt(txtRecordName(domainRecord.domain));
      matched = records.some((set) => set.join("").includes(domainRecord.verificationToken));
    } else {
      const records = await this.dnsVerifier.resolveCname(cnameRecordName(domainRecord.domain));
      matched = records.some((value) => value.includes(domainRecord.verificationToken));
    }

    if (!matched) {
      await this.domainRepo.touchLastChecked(id, now);
      throw new DomainVerificationFailedError(domainRecord.domain);
    }

    return this.domainRepo.updateStatus(id, "VERIFIED", { verifiedAt: now, lastCheckedAt: now });
  }

  async activate(tenantId: string, id: string): Promise<TenantDomainRecord> {
    const domainRecord = await this.findOrThrow(id);
    if (domainRecord.tenantId !== tenantId) throw new DomainNotFoundError(id);
    if (domainRecord.status !== "VERIFIED") {
      throw new InvalidDomainStatusTransitionError(domainRecord.status, "ACTIVE");
    }
    return this.domainRepo.updateStatus(id, "ACTIVE");
  }

  async detach(tenantId: string, id: string): Promise<TenantDomainRecord> {
    const domainRecord = await this.findOrThrow(id);
    if (domainRecord.tenantId !== tenantId) throw new DomainNotFoundError(id);
    if (domainRecord.status === "DETACHED") {
      throw new InvalidDomainStatusTransitionError(domainRecord.status, "DETACHED");
    }
    return this.domainRepo.updateStatus(id, "DETACHED");
  }

  async setPrimary(tenantId: string, id: string): Promise<TenantDomainRecord> {
    const domainRecord = await this.findOrThrow(id);
    if (domainRecord.tenantId !== tenantId) throw new DomainNotFoundError(id);
    if (domainRecord.status !== "ACTIVE") {
      throw new InvalidDomainStatusTransitionError(domainRecord.status, "PRIMARY");
    }
    return this.domainRepo.setPrimary(tenantId, id);
  }
}
