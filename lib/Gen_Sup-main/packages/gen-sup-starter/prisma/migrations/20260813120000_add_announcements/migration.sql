-- CreateTable
CREATE TABLE "announcements" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "title" VARCHAR(500) NOT NULL,
    "body" TEXT NOT NULL,
    "type" VARCHAR(24) NOT NULL DEFAULT 'INFO',
    "target_segment" VARCHAR(64) NOT NULL DEFAULT 'ALL',
    "channels" TEXT[],
    "scheduled_for" TIMESTAMPTZ(6),
    "sent_at" TIMESTAMPTZ(6),
    "created_by" UUID NOT NULL,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "announcements_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "announcements_sent_at_idx" ON "announcements"("sent_at");

-- CreateIndex
CREATE INDEX "announcements_scheduled_for_idx" ON "announcements"("scheduled_for");
