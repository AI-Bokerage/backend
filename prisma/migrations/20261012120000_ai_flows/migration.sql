-- AlterTable
ALTER TABLE "Task" ADD COLUMN     "suggested" BOOLEAN NOT NULL DEFAULT false;

-- CreateTable
CREATE TABLE "EmailDraft" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "brokerUserId" TEXT NOT NULL,
    "leadId" TEXT NOT NULL,
    "intent" TEXT,
    "subject" TEXT NOT NULL,
    "body" TEXT NOT NULL,
    "aiAssisted" BOOLEAN NOT NULL DEFAULT false,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "EmailDraft_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "CrmUserSettings" (
    "userId" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "aiEnabled" BOOLEAN NOT NULL DEFAULT true,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "CrmUserSettings_pkey" PRIMARY KEY ("userId")
);

-- CreateIndex
CREATE INDEX "EmailDraft_tenantId_idx" ON "EmailDraft"("tenantId");

-- CreateIndex
CREATE INDEX "EmailDraft_leadId_idx" ON "EmailDraft"("leadId");

-- AddForeignKey
ALTER TABLE "EmailDraft" ADD CONSTRAINT "EmailDraft_leadId_fkey" FOREIGN KEY ("leadId") REFERENCES "Lead"("id") ON DELETE CASCADE ON UPDATE CASCADE;

