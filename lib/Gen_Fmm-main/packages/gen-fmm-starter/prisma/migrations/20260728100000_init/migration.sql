-- CreateTable
CREATE TABLE "module" (
    "code" VARCHAR(32) NOT NULL,
    "name" VARCHAR(120) NOT NULL,
    "description" TEXT,
    "category" VARCHAR(60),
    "is_active" BOOLEAN NOT NULL DEFAULT true,
    "display_order" INTEGER NOT NULL DEFAULT 0,
    "icon_key" VARCHAR(60),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "module_pkey" PRIMARY KEY ("code")
);

-- CreateTable
CREATE TABLE "plan_module" (
    "plan_code" VARCHAR(60) NOT NULL,
    "module_code" VARCHAR(32) NOT NULL,
    "entitlement" JSONB NOT NULL DEFAULT '{}',
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "plan_module_pkey" PRIMARY KEY ("plan_code","module_code")
);

-- CreateTable
CREATE TABLE "feature_flag" (
    "key" VARCHAR(128) NOT NULL,
    "module_code" VARCHAR(32),
    "default_enabled" BOOLEAN NOT NULL DEFAULT false,
    "is_gradual_rollout" BOOLEAN NOT NULL DEFAULT false,
    "rollout_percentage" SMALLINT NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "feature_flag_pkey" PRIMARY KEY ("key")
);

-- CreateTable
CREATE TABLE "tenant_feature_flag" (
    "tenant_id" UUID NOT NULL,
    "flag_key" VARCHAR(128) NOT NULL,
    "enabled" BOOLEAN NOT NULL,
    "config" JSONB NOT NULL DEFAULT '{}',
    "reason" VARCHAR(300),
    "expires_at" TIMESTAMPTZ(6),
    "created_by" VARCHAR(120),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "tenant_feature_flag_pkey" PRIMARY KEY ("tenant_id","flag_key")
);

-- CreateTable
CREATE TABLE "feature_usage_event" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "flag_key" VARCHAR(128) NOT NULL,
    "enabled" BOOLEAN NOT NULL,
    "reason" VARCHAR(30) NOT NULL,
    "plan_code" VARCHAR(60),
    "occurred_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "feature_usage_event_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "plan_module_module_code_idx" ON "plan_module"("module_code");

-- CreateIndex
CREATE INDEX "feature_flag_module_code_idx" ON "feature_flag"("module_code");

-- CreateIndex
CREATE INDEX "feature_usage_event_tenant_id_flag_key_occurred_at_idx" ON "feature_usage_event"("tenant_id", "flag_key", "occurred_at" DESC);

-- AddForeignKey
ALTER TABLE "plan_module" ADD CONSTRAINT "plan_module_module_code_fkey" FOREIGN KEY ("module_code") REFERENCES "module"("code") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "feature_flag" ADD CONSTRAINT "feature_flag_module_code_fkey" FOREIGN KEY ("module_code") REFERENCES "module"("code") ON DELETE SET NULL ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "tenant_feature_flag" ADD CONSTRAINT "tenant_feature_flag_flag_key_fkey" FOREIGN KEY ("flag_key") REFERENCES "feature_flag"("key") ON DELETE CASCADE ON UPDATE CASCADE;
