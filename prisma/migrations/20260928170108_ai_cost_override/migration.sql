-- CreateTable
CREATE TABLE "AiCostLimitOverride" (
    "tenantId" TEXT NOT NULL,
    "expiresAt" TIMESTAMP(3) NOT NULL,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "AiCostLimitOverride_pkey" PRIMARY KEY ("tenantId")
);
