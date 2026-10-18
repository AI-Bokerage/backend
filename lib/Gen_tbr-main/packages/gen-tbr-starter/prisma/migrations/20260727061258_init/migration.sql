-- CreateEnum
CREATE TYPE "BrandingTheme" AS ENUM ('SYSTEM', 'LIGHT', 'DARK');

-- CreateEnum
CREATE TYPE "DomainStatus" AS ENUM ('PENDING', 'VERIFIED', 'ACTIVE', 'DETACHED');

-- CreateEnum
CREATE TYPE "DomainVerificationMethod" AS ENUM ('TXT', 'CNAME');

-- CreateTable
CREATE TABLE "tenant_branding" (
    "tenantId" UUID NOT NULL,
    "displayName" VARCHAR(120) NOT NULL,
    "tagline" VARCHAR(200),
    "logoUrl" VARCHAR(1024),
    "logoDarkUrl" VARCHAR(1024),
    "faviconUrl" VARCHAR(1024),
    "primaryColor" VARCHAR(32),
    "secondaryColor" VARCHAR(32),
    "accentColor" VARCHAR(32),
    "fontFamily" VARCHAR(120),
    "theme" "BrandingTheme" NOT NULL DEFAULT 'SYSTEM',
    "rawMeta" JSONB,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "tenant_branding_pkey" PRIMARY KEY ("tenantId")
);

-- CreateTable
CREATE TABLE "tenant_domain" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenantId" UUID NOT NULL,
    "domain" VARCHAR(253) NOT NULL,
    "verificationToken" VARCHAR(64) NOT NULL,
    "status" "DomainStatus" NOT NULL DEFAULT 'PENDING',
    "verificationMethod" "DomainVerificationMethod" NOT NULL,
    "verifiedAt" TIMESTAMPTZ(6),
    "lastCheckedAt" TIMESTAMPTZ(6),
    "isPrimary" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "tenant_domain_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "tenant_domain_tenantId_idx" ON "tenant_domain"("tenantId");

-- CreateIndex
CREATE UNIQUE INDEX "tenant_domain_domain_key" ON "tenant_domain"("domain");
