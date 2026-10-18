import "express-async-errors";
import express, { type Express } from "express";
import helmet from "helmet";
import cors from "cors";
import { getPrismaClient } from "./infra/persistence/prisma-client.ts";
import { PrismaTenantBrandingRepo } from "./modules/branding/v1/repo.ts";
import { PrismaTenantDomainRepo } from "./modules/domains/v1/repo.ts";
import { S3AssetStore } from "./infra/storage/s3-asset-store.ts";
import { NodeDnsVerifier } from "./infra/dns/node-dns-verifier.ts";
import { HttpTntClient } from "./infra/tnt-client/tnt-client.ts";
import { BrandingService } from "./modules/branding/v1/branding.service.ts";
import { DomainService } from "./modules/domains/v1/domain.service.ts";
import { createBrandingRouter } from "./modules/branding/v1/branding.router.ts";
import { createDomainsRouter } from "./modules/domains/v1/domain.router.ts";
import { errorHandler } from "./middleware/error-handler.ts";
import { requireEnv, optionalEnv } from "./config/env.ts";
import { GenTbrConfigError } from "./common/errors.ts";
import type { ITenantBrandingRepo } from "./domain/ports/tenant-branding.repository.port.ts";
import type { ITenantDomainRepo } from "./domain/ports/tenant-domain.repository.port.ts";
import type { IAssetStore } from "./domain/ports/asset-store.port.ts";
import type { IDnsVerifier } from "./domain/ports/dns-verifier.port.ts";
import type { ITntClient } from "./domain/ports/tnt-client.port.ts";

export interface GenTbrModulesConfig {
  branding?: boolean;
  domains?: boolean;
  manifest?: boolean;
  assets?: boolean;
}

export interface GenTbrConfig {
  brandingRepo?: ITenantBrandingRepo;
  domainRepo?: ITenantDomainRepo;
  assetStore?: IAssetStore;
  dnsVerifier?: IDnsVerifier;
  tntClient?: ITntClient;
  modules?: GenTbrModulesConfig;
  internalSecret?: string;
}

export interface GenTbrInstance {
  app: Express;
}

function resolveBrandingRepo(override?: ITenantBrandingRepo): ITenantBrandingRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaTenantBrandingRepo(getPrismaClient());
}

function resolveDomainRepo(override?: ITenantDomainRepo): ITenantDomainRepo {
  if (override) return override;
  requireEnv("DATABASE_URL");
  return new PrismaTenantDomainRepo(getPrismaClient());
}

function resolveAssetStore(override?: IAssetStore): IAssetStore {
  if (override) return override;
  return new S3AssetStore({
    bucket: requireEnv("GEN_TBR_S3_BUCKET"),
    region: requireEnv("GEN_TBR_S3_REGION"),
    endpoint: optionalEnv("GEN_TBR_S3_ENDPOINT"),
    accessKeyId: requireEnv("GEN_TBR_S3_ACCESS_KEY_ID"),
    secretAccessKey: requireEnv("GEN_TBR_S3_SECRET_ACCESS_KEY"),
  });
}

function resolveDnsVerifier(override?: IDnsVerifier): IDnsVerifier {
  return override ?? new NodeDnsVerifier();
}

function resolveTntClient(override?: ITntClient): ITntClient | undefined {
  if (override) return override;
  const baseUrl = optionalEnv("GEN_TNT_BASE_URL");
  return baseUrl ? new HttpTntClient({ baseUrl }) : undefined;
}

function resolveInternalSecret(override?: string): string {
  return override ?? requireEnv("GEN_TBR_INTERNAL_SECRET");
}

export function createGenTbr(config: GenTbrConfig): GenTbrInstance {
  const modules: Required<GenTbrModulesConfig> = {
    branding: config.modules?.branding ?? true,
    domains: config.modules?.domains ?? true,
    manifest: config.modules?.manifest ?? true,
    assets: config.modules?.assets ?? true,
  };

  if (!modules.branding && !modules.domains) {
    throw new GenTbrConfigError("At least one of modules.branding or modules.domains must be enabled");
  }

  const internalSecretValue = resolveInternalSecret(config.internalSecret);

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json());

  app.get("/health", (_req, res) => {
    res.json({ status: "ok" });
  });

  // Resolved once and shared: BrandingService needs the domain repo for its
  // domain-based manifest lookup, and DomainService needs it for its own CRUD
  // routes. Resolving twice would just build two wrapper objects around the
  // same getPrismaClient() singleton, but it's needless duplicate work (and
  // duplicate requireEnv checks), so share a single instance across both.
  const domainRepo =
    modules.branding || modules.domains ? resolveDomainRepo(config.domainRepo) : undefined;

  if (modules.branding) {
    const brandingRepo = resolveBrandingRepo(config.brandingRepo);
    const assetStore = modules.assets ? resolveAssetStore(config.assetStore) : undefined;
    const brandingService = new BrandingService(brandingRepo, domainRepo!);
    app.use(
      "/api/v1/branding",
      createBrandingRouter({
        brandingService,
        assetStore,
        internalSecretValue,
        assetsEnabled: modules.assets,
        manifestEnabled: modules.manifest,
      }),
    );
  }

  if (modules.domains) {
    const dnsVerifier = resolveDnsVerifier(config.dnsVerifier);
    const domainService = new DomainService(domainRepo!, dnsVerifier);
    app.use("/api/v1/domains", createDomainsRouter({ domainService, internalSecretValue }));
  }

  // resolveTntClient is exercised for its resolution behavior even though no v1
  // route consumes it yet (see spec §7.2 — a future re-verify hook).
  resolveTntClient(config.tntClient);

  app.use(errorHandler);

  return { app };
}
