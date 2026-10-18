-- CreateTable
CREATE TABLE "TenantProfile" (
    "tenantId" TEXT NOT NULL,
    "displayName" TEXT,
    "supportEmail" TEXT,
    "phone" TEXT,
    "address" TEXT,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "TenantProfile_pkey" PRIMARY KEY ("tenantId")
);

-- CreateTable
CREATE TABLE "BookingPageSettings" (
    "tenantId" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT false,
    "bufferMinutes" INTEGER NOT NULL DEFAULT 15,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "BookingPageSettings_pkey" PRIMARY KEY ("tenantId")
);

-- CreateIndex
CREATE UNIQUE INDEX "BookingPageSettings_slug_key" ON "BookingPageSettings"("slug");
