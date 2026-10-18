-- BSM-SVC transactional outbox for subscription lifecycle events.
-- Events are written atomically with domain state changes and dispatched
-- asynchronously via BsmOutboxPublisher to guarantee at-least-once delivery.

CREATE TABLE IF NOT EXISTS bsm_outbox_events (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    routing_key     VARCHAR(200) NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER      NOT NULL DEFAULT 0,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_bsm_outbox_status_created ON bsm_outbox_events (status, created_at)
    WHERE status IN ('PENDING', 'IN_FLIGHT');
