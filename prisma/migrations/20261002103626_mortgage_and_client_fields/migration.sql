-- AlterTable
ALTER TABLE "Lead" ADD COLUMN     "downPayment" DOUBLE PRECISION,
ADD COLUMN     "income" DOUBLE PRECISION,
ADD COLUMN     "monthlyDebts" DOUBLE PRECISION,
ADD COLUMN     "notes" TEXT,
ADD COLUMN     "propertyAddress" TEXT,
ADD COLUMN     "propertyValue" DOUBLE PRECISION;

-- CreateTable
CREATE TABLE "Mortgage" (
    "id" TEXT NOT NULL,
    "leadId" TEXT NOT NULL,
    "lender" TEXT NOT NULL,
    "interestRate" DOUBLE PRECISION NOT NULL,
    "balance" DOUBLE PRECISION NOT NULL,
    "monthlyPayment" DOUBLE PRECISION NOT NULL,
    "maturityDate" TIMESTAMP(3) NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "Mortgage_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Mortgage_leadId_idx" ON "Mortgage"("leadId");

-- CreateIndex
CREATE INDEX "Mortgage_maturityDate_idx" ON "Mortgage"("maturityDate");

-- AddForeignKey
ALTER TABLE "Mortgage" ADD CONSTRAINT "Mortgage_leadId_fkey" FOREIGN KEY ("leadId") REFERENCES "Lead"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
