-- CreateTable
CREATE TABLE "revenue_snapshots" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "period" VARCHAR(16) NOT NULL,
    "captured_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "total_mrr" DOUBLE PRECISION NOT NULL,
    "total_arr" DOUBLE PRECISION NOT NULL,
    "average_revenue_per_tenant" DOUBLE PRECISION NOT NULL,
    "by_plan" JSONB NOT NULL,
    "by_region" JSONB NOT NULL,
    "plan_distribution" JSONB NOT NULL,

    CONSTRAINT "revenue_snapshots_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "revenue_snapshots_period_captured_at_idx" ON "revenue_snapshots"("period", "captured_at");
