-- CreateTable
CREATE TABLE "sla_policies" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "name" VARCHAR(128) NOT NULL,
    "entity_type" VARCHAR(64) NOT NULL,
    "sla_type" VARCHAR(64) NOT NULL,
    "duration_mins" INTEGER NOT NULL,
    "warning_mins" INTEGER NOT NULL,
    "is_enabled" BOOLEAN NOT NULL DEFAULT true,
    "description" TEXT,
    "created_by" VARCHAR(120),
    "updated_by" VARCHAR(120),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "sla_policies_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sla_instances" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "policy_id" UUID NOT NULL,
    "entity_type" VARCHAR(64) NOT NULL,
    "entity_id" UUID NOT NULL,
    "sla_type" VARCHAR(64) NOT NULL,
    "status" VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    "started_at" TIMESTAMPTZ(6) NOT NULL,
    "due_at" TIMESTAMPTZ(6) NOT NULL,
    "warning_at" TIMESTAMPTZ(6) NOT NULL,
    "breached_at" TIMESTAMPTZ(6),
    "resolved_at" TIMESTAMPTZ(6),
    "metadata" JSONB NOT NULL DEFAULT '{}',

    CONSTRAINT "sla_instances_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sla_history" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "instance_id" UUID NOT NULL,
    "from_status" VARCHAR(16),
    "to_status" VARCHAR(16) NOT NULL,
    "note" TEXT,
    "occurred_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "sla_history_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sla_escalations" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "instance_id" UUID NOT NULL,
    "level" INTEGER NOT NULL DEFAULT 1,
    "event_type" VARCHAR(64) NOT NULL,
    "notified" BOOLEAN NOT NULL DEFAULT false,
    "escalated_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "sla_escalations_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "sla_outbox_events" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "event_type" VARCHAR(128) NOT NULL,
    "exchange" VARCHAR(128) NOT NULL,
    "routing_key" VARCHAR(128) NOT NULL,
    "payload" JSONB NOT NULL,
    "status" VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    "retry_count" INTEGER NOT NULL DEFAULT 0,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "sla_outbox_events_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "sla_policies_tenant_id_idx" ON "sla_policies"("tenant_id");

-- CreateIndex
CREATE INDEX "sla_policies_tenant_id_entity_type_idx" ON "sla_policies"("tenant_id", "entity_type");

-- CreateIndex
CREATE INDEX "sla_policies_is_enabled_idx" ON "sla_policies"("is_enabled");

-- CreateIndex
CREATE UNIQUE INDEX "sla_policies_tenant_id_entity_type_sla_type_key" ON "sla_policies"("tenant_id", "entity_type", "sla_type");

-- CreateIndex
CREATE INDEX "sla_instances_tenant_id_idx" ON "sla_instances"("tenant_id");

-- CreateIndex
CREATE INDEX "sla_instances_status_idx" ON "sla_instances"("status");

-- CreateIndex
CREATE INDEX "sla_instances_due_at_idx" ON "sla_instances"("due_at");

-- CreateIndex
CREATE INDEX "sla_instances_warning_at_idx" ON "sla_instances"("warning_at");

-- CreateIndex
CREATE INDEX "sla_instances_tenant_id_entity_type_status_idx" ON "sla_instances"("tenant_id", "entity_type", "status");

-- CreateIndex
CREATE INDEX "sla_instances_tenant_id_status_due_at_idx" ON "sla_instances"("tenant_id", "status", "due_at");

-- CreateIndex
CREATE UNIQUE INDEX "sla_instances_tenant_id_entity_id_sla_type_key" ON "sla_instances"("tenant_id", "entity_id", "sla_type");

-- CreateIndex
CREATE INDEX "sla_history_instance_id_idx" ON "sla_history"("instance_id");

-- CreateIndex
CREATE INDEX "sla_history_tenant_id_occurred_at_idx" ON "sla_history"("tenant_id", "occurred_at");

-- CreateIndex
CREATE INDEX "sla_escalations_instance_id_idx" ON "sla_escalations"("instance_id");

-- CreateIndex
CREATE INDEX "sla_escalations_tenant_id_escalated_at_idx" ON "sla_escalations"("tenant_id", "escalated_at");

-- CreateIndex
CREATE INDEX "sla_outbox_events_status_created_at_idx" ON "sla_outbox_events"("status", "created_at");

-- CreateIndex
CREATE INDEX "sla_outbox_events_tenant_id_idx" ON "sla_outbox_events"("tenant_id");

-- AddForeignKey
ALTER TABLE "sla_instances" ADD CONSTRAINT "sla_instances_policy_id_fkey" FOREIGN KEY ("policy_id") REFERENCES "sla_policies"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "sla_history" ADD CONSTRAINT "sla_history_instance_id_fkey" FOREIGN KEY ("instance_id") REFERENCES "sla_instances"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "sla_escalations" ADD CONSTRAINT "sla_escalations_instance_id_fkey" FOREIGN KEY ("instance_id") REFERENCES "sla_instances"("id") ON DELETE CASCADE ON UPDATE CASCADE;
