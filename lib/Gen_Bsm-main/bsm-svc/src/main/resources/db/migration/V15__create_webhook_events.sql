-- Flyway V15: Incoming webhook events with idempotency tracking
CREATE TABLE IF NOT EXISTS webhook_events (
    id                  UUID PRIMARY KEY,
    provider            VARCHAR(50) NOT NULL,
    external_event_id   VARCHAR(255) NOT NULL,
    event_type          VARCHAR(100) NOT NULL,
    payload             TEXT NOT NULL,
    status              VARCHAR(50) NOT NULL,
    failure_reason      TEXT,
    received_at         TIMESTAMPTZ NOT NULL,
    processed_at        TIMESTAMPTZ,
    CONSTRAINT uq_webhook_event_per_provider UNIQUE (provider, external_event_id)
);

CREATE INDEX IF NOT EXISTS idx_webhook_events_provider_ext ON webhook_events(provider, external_event_id);
CREATE INDEX IF NOT EXISTS idx_webhook_events_status ON webhook_events(status);
