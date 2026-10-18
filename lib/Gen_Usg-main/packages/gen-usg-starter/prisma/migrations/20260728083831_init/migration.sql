-- CreateEnum
CREATE TYPE "rollup_period" AS ENUM ('daily', 'monthly');

-- CreateEnum
CREATE TYPE "grace_overage_status" AS ENUM ('OPEN', 'CLOSED');

-- CreateTable
CREATE TABLE "usage_snapshot" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "snapshot_at" TIMESTAMPTZ(6) NOT NULL,
    "period" "rollup_period" NOT NULL,
    "metrics" JSONB NOT NULL,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "usage_snapshot_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "usage_grace_overage" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "metric" VARCHAR(120) NOT NULL,
    "grace_started_at" TIMESTAMPTZ(6) NOT NULL,
    "grace_expires_at" TIMESTAMPTZ(6) NOT NULL,
    "overage_count" INTEGER NOT NULL DEFAULT 0,
    "status" "grace_overage_status" NOT NULL DEFAULT 'OPEN',
    "billed_at" TIMESTAMPTZ(6),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "usage_grace_overage_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "usage_reconciliation_log" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "metric" VARCHAR(120) NOT NULL,
    "counter_value" BIGINT NOT NULL,
    "db_value" BIGINT NOT NULL,
    "drift_pct" DECIMAL(6,2) NOT NULL,
    "corrected" BOOLEAN NOT NULL,
    "run_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "usage_reconciliation_log_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "usage_idempotency_ledger" (
    "event_id" VARCHAR(128) NOT NULL,
    "processed_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "usage_idempotency_ledger_pkey" PRIMARY KEY ("event_id")
);

-- CreateIndex
CREATE INDEX "usage_snapshot_tenant_id_snapshot_at_idx" ON "usage_snapshot"("tenant_id", "snapshot_at" DESC);

-- CreateIndex
CREATE INDEX "usage_snapshot_tenant_id_period_snapshot_at_idx" ON "usage_snapshot"("tenant_id", "period", "snapshot_at" DESC);

-- CreateIndex
CREATE INDEX "usage_grace_overage_status_grace_expires_at_idx" ON "usage_grace_overage"("status", "grace_expires_at");

-- CreateIndex
CREATE UNIQUE INDEX "usage_grace_overage_tenant_id_metric_status_key" ON "usage_grace_overage"("tenant_id", "metric", "status");

-- CreateIndex
CREATE INDEX "usage_reconciliation_log_tenant_id_run_at_idx" ON "usage_reconciliation_log"("tenant_id", "run_at" DESC);
