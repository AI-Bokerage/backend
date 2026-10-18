-- V30: Idempotent recreation of bsm_outbox_events.
--
-- Problem: Flyway history (flyway_schema_history_bsm) records V26 and V29 as
-- "successfully applied", but the actual bsm_outbox_events table does not exist
-- in the database (dropped manually or via a database reset that preserved the
-- Flyway history table).  Flyway therefore runs no new migrations, and Hibernate's
-- schema validation (ddl-auto=validate) reports "missing table [bsm_outbox_events]".
--
-- Fix: CREATE TABLE IF NOT EXISTS is idempotent.
--   * If the table already exists with the correct schema  → no-op, zero impact.
--   * If the table was dropped after V26/V29 ran           → recreates it in full,
--     including the version column added by V29, so the schema is immediately
--     consistent with BsmOutboxEventEntity.
--
-- This migration supersedes the original V26 + V29 DDL for the bsm_outbox_events table.

CREATE TABLE IF NOT EXISTS bsm_outbox_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    routing_key     VARCHAR(200) NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version         BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bsm_outbox_status_created
    ON bsm_outbox_events (status, created_at)
    WHERE status IN ('PENDING', 'IN_FLIGHT');
