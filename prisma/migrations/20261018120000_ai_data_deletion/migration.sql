-- Flow 4.11 / Flow 1: AI copies of deleted people still to be wiped (retried hourly).
CREATE TABLE "AiDataDeletion" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "clientId" TEXT NOT NULL,
    "pending" TEXT[],
    "attempts" INTEGER NOT NULL DEFAULT 0,
    "lastError" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,
    CONSTRAINT "AiDataDeletion_pkey" PRIMARY KEY ("id")
);
CREATE UNIQUE INDEX "AiDataDeletion_tenantId_clientId_key" ON "AiDataDeletion"("tenantId", "clientId");
