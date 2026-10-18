-- CreateEnum
CREATE TYPE "notif_channel" AS ENUM ('email', 'inapp', 'sms', 'webhook');

-- CreateEnum
CREATE TYPE "notification_status" AS ENUM ('SENT', 'FAILED', 'QUEUED_FOR_DIGEST', 'READ');

-- CreateEnum
CREATE TYPE "digest_status" AS ENUM ('PENDING', 'PROCESSING', 'SENT', 'FAILED');

-- CreateTable
CREATE TABLE "notification_preference" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "event_type" VARCHAR(120) NOT NULL,
    "channel" "notif_channel" NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "digest_mode" BOOLEAN NOT NULL DEFAULT false,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "notification_preference_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "notification_log" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "channel" "notif_channel" NOT NULL,
    "event_type" VARCHAR(120) NOT NULL,
    "title" VARCHAR(300) NOT NULL,
    "body" TEXT NOT NULL,
    "entity_ref_type" VARCHAR(60),
    "entity_ref_id" VARCHAR(120),
    "status" "notification_status" NOT NULL,
    "read_at" TIMESTAMPTZ(6),
    "attempts" INTEGER NOT NULL DEFAULT 1,
    "last_error" TEXT,
    "sent_at" TIMESTAMPTZ(6),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "notification_log_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "digest_queue_entry" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "email" VARCHAR(320) NOT NULL,
    "channel" "notif_channel" NOT NULL,
    "scheduled_for" TIMESTAMPTZ(6) NOT NULL,
    "status" "digest_status" NOT NULL DEFAULT 'PENDING',
    "notification_ids" UUID[],
    "attempts" INTEGER NOT NULL DEFAULT 0,
    "last_error" TEXT,
    "sent_at" TIMESTAMPTZ(6),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "digest_queue_entry_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "webhook_endpoint" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "tenant_id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "url" VARCHAR(2048) NOT NULL,
    "secret" VARCHAR(128) NOT NULL,
    "enabled" BOOLEAN NOT NULL DEFAULT true,
    "description" VARCHAR(300),
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "webhook_endpoint_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "email_suppression" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "email" VARCHAR(320) NOT NULL,
    "reason" VARCHAR(60) NOT NULL,
    "suppressed_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "email_suppression_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "notification_preference_tenant_id_user_id_idx" ON "notification_preference"("tenant_id", "user_id");

-- CreateIndex
CREATE UNIQUE INDEX "notification_preference_tenant_id_user_id_event_type_channe_key" ON "notification_preference"("tenant_id", "user_id", "event_type", "channel");

-- CreateIndex
CREATE INDEX "notification_log_tenant_id_user_id_status_created_at_idx" ON "notification_log"("tenant_id", "user_id", "status", "created_at" DESC);

-- CreateIndex
CREATE INDEX "digest_queue_entry_status_scheduled_for_idx" ON "digest_queue_entry"("status", "scheduled_for");

-- CreateIndex
CREATE INDEX "digest_queue_entry_tenant_id_user_id_status_idx" ON "digest_queue_entry"("tenant_id", "user_id", "status");

-- CreateIndex
CREATE INDEX "webhook_endpoint_tenant_id_user_id_enabled_idx" ON "webhook_endpoint"("tenant_id", "user_id", "enabled");

-- CreateIndex
CREATE UNIQUE INDEX "email_suppression_email_key" ON "email_suppression"("email");
