-- CreateEnum
CREATE TYPE "FileStatus" AS ENUM ('ACTIVE', 'INACTIVE');

-- CreateEnum
CREATE TYPE "EmploymentType" AS ENUM ('EMPLOYED', 'SELF_EMPLOYED', 'CORPORATION');

-- AlterTable
ALTER TABLE "Lead" ADD COLUMN     "city" TEXT,
ADD COLUMN     "fileStatus" "FileStatus" NOT NULL DEFAULT 'ACTIVE';

-- CreateTable
CREATE TABLE "Applicant" (
    "id" TEXT NOT NULL,
    "leadId" TEXT NOT NULL,
    "firstName" TEXT NOT NULL,
    "lastName" TEXT NOT NULL,
    "email" TEXT,
    "phone" TEXT,
    "address" TEXT,
    "dateOfBirth" TIMESTAMP(3),
    "brokerNotes" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Applicant_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Employment" (
    "id" TEXT NOT NULL,
    "applicantId" TEXT NOT NULL,
    "type" "EmploymentType" NOT NULL,
    "employer" TEXT,
    "position" TEXT,
    "startDate" TIMESTAMP(3),
    "baseSalary" DOUBLE PRECISION,
    "t4Values" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Employment_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "NoaIncome" (
    "id" TEXT NOT NULL,
    "applicantId" TEXT NOT NULL,
    "taxYear" INTEGER NOT NULL,
    "totalIncome" DOUBLE PRECISION NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "NoaIncome_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "Debt" (
    "id" TEXT NOT NULL,
    "leadId" TEXT NOT NULL,
    "description" TEXT,
    "balance" DOUBLE PRECISION NOT NULL,
    "monthlyPayment" DOUBLE PRECISION NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Debt_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PropertyRecord" (
    "id" TEXT NOT NULL,
    "leadId" TEXT NOT NULL,
    "address" TEXT NOT NULL,
    "propertyTaxes" DOUBLE PRECISION,
    "isCondo" BOOLEAN NOT NULL DEFAULT false,
    "isRental" BOOLEAN NOT NULL DEFAULT false,
    "mortgageBalance" DOUBLE PRECISION,
    "mortgageLender" TEXT,
    "maturityDate" TIMESTAMP(3),
    "ownership" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PropertyRecord_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "FundsSource" (
    "id" TEXT NOT NULL,
    "leadId" TEXT NOT NULL,
    "accountType" TEXT NOT NULL,
    "amount" DOUBLE PRECISION NOT NULL,
    "isGift" BOOLEAN NOT NULL DEFAULT false,
    "giftSource" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "FundsSource_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "FollowUp" (
    "id" TEXT NOT NULL,
    "leadId" TEXT NOT NULL,
    "type" TEXT NOT NULL,
    "scheduledAt" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "FollowUp_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Applicant_leadId_idx" ON "Applicant"("leadId");

-- CreateIndex
CREATE INDEX "Employment_applicantId_idx" ON "Employment"("applicantId");

-- CreateIndex
CREATE UNIQUE INDEX "NoaIncome_applicantId_taxYear_key" ON "NoaIncome"("applicantId", "taxYear");

-- CreateIndex
CREATE INDEX "Debt_leadId_idx" ON "Debt"("leadId");

-- CreateIndex
CREATE INDEX "PropertyRecord_leadId_idx" ON "PropertyRecord"("leadId");

-- CreateIndex
CREATE INDEX "FundsSource_leadId_idx" ON "FundsSource"("leadId");

-- CreateIndex
CREATE INDEX "FollowUp_leadId_idx" ON "FollowUp"("leadId");

-- AddForeignKey
ALTER TABLE "Applicant" ADD CONSTRAINT "Applicant_leadId_fkey" FOREIGN KEY ("leadId") REFERENCES "Lead"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Employment" ADD CONSTRAINT "Employment_applicantId_fkey" FOREIGN KEY ("applicantId") REFERENCES "Applicant"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "NoaIncome" ADD CONSTRAINT "NoaIncome_applicantId_fkey" FOREIGN KEY ("applicantId") REFERENCES "Applicant"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "Debt" ADD CONSTRAINT "Debt_leadId_fkey" FOREIGN KEY ("leadId") REFERENCES "Lead"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PropertyRecord" ADD CONSTRAINT "PropertyRecord_leadId_fkey" FOREIGN KEY ("leadId") REFERENCES "Lead"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "FundsSource" ADD CONSTRAINT "FundsSource_leadId_fkey" FOREIGN KEY ("leadId") REFERENCES "Lead"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "FollowUp" ADD CONSTRAINT "FollowUp_leadId_fkey" FOREIGN KEY ("leadId") REFERENCES "Lead"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
