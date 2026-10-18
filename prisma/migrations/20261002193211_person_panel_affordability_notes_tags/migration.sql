-- AlterTable
ALTER TABLE "Lead" ADD COLUMN     "affordabilitySnapshot" JSONB,
ADD COLUMN     "bankDetails" TEXT,
ADD COLUMN     "brokerNotes" TEXT,
ADD COLUMN     "dateOfBirth" TIMESTAMP(3),
ADD COLUMN     "sin" TEXT,
ADD COLUMN     "tags" TEXT[] DEFAULT ARRAY[]::TEXT[];
