-- Flow 4.5: files are read by the AI document service in the background; its review holds the
-- values read until the broker approves them, so the CRM no longer keeps an "extracted" copy.
ALTER TYPE "DocumentReadStatus" ADD VALUE 'PENDING' BEFORE 'READ';
ALTER TABLE "LeadDocument" ADD COLUMN "aiDocumentId" TEXT;
ALTER TABLE "LeadDocument" DROP COLUMN "extracted";
