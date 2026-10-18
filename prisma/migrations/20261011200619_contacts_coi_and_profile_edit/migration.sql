-- CreateEnum
CREATE TYPE "ContactCategory" AS ENUM ('REALTOR', 'LAWYER', 'APPRAISER', 'INSURANCE', 'ACCOUNTANT', 'HOME_INSPECTOR', 'FINANCIAL_ADVISOR');

-- CreateTable
CREATE TABLE "Contact" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "brokerUserId" TEXT NOT NULL,
    "category" "ContactCategory" NOT NULL,
    "name" TEXT NOT NULL,
    "company" TEXT,
    "phone" TEXT,
    "email" TEXT,
    "dateOfBirth" TIMESTAMP(3),
    "address" TEXT,
    "city" TEXT,
    "lastContactedAt" TIMESTAMP(3),
    "notes" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "Contact_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Contact_tenantId_idx" ON "Contact"("tenantId");

-- CreateIndex
CREATE INDEX "Contact_brokerUserId_idx" ON "Contact"("brokerUserId");

-- CreateIndex
CREATE INDEX "Contact_category_idx" ON "Contact"("category");
