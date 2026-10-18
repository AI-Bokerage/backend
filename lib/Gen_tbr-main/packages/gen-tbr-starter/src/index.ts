export { createGenTbr } from "./create-gen-tbr.ts";
export type { GenTbrConfig, GenTbrModulesConfig, GenTbrInstance } from "./create-gen-tbr.ts";

export type { ITenantBrandingRepo, BrandingRecord, UpsertBrandingInput, PatchBrandingInput } from "./domain/ports/tenant-branding.repository.port.ts";
export type { ITenantDomainRepo, TenantDomainRecord, CreateDomainInput } from "./domain/ports/tenant-domain.repository.port.ts";
export type { IAssetStore, AssetRef, PutAssetResult } from "./domain/ports/asset-store.port.ts";
export type { IDnsVerifier, DnsRecord, DnsRecordKind } from "./domain/ports/dns-verifier.port.ts";
export type { ITntClient } from "./domain/ports/tnt-client.port.ts";

export { PrismaTenantBrandingRepo } from "./modules/branding/v1/repo.ts";
export { PrismaTenantDomainRepo } from "./modules/domains/v1/repo.ts";
export { S3AssetStore } from "./infra/storage/s3-asset-store.ts";
export { NodeDnsVerifier } from "./infra/dns/node-dns-verifier.ts";
export { HttpTntClient } from "./infra/tnt-client/tnt-client.ts";
export { getPrismaClient } from "./infra/persistence/prisma-client.ts";

export {
  AppError,
  GenTbrConfigError,
  BrandingNotFoundError,
  BrandingValidationError,
  DomainNotFoundError,
  DomainAlreadyClaimedError,
  DomainVerificationFailedError,
  InvalidDomainStatusTransitionError,
  AssetStoreError,
  AssetNotFoundError,
} from "./common/errors.ts";
