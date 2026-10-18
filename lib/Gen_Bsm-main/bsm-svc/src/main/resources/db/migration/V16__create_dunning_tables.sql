-- Flyway V16: Dunning engine — attempts table + dunning fields on subscriptions
CREATE TABLE IF NOT EXISTS dunning_attempts (
    id                   UUID PRIMARY KEY,
    subscription_id      UUID NOT NULL,
    tenant_id            UUID NOT NULL,
    invoice_id           UUID NOT NULL,
    attempt_number       INTEGER NOT NULL,
    status               VARCHAR(50) NOT NULL,
    failure_code         VARCHAR(255),
    failure_message      TEXT,
    external_payment_id  VARCHAR(255),
    next_retry_at        TIMESTAMPTZ,
    attempted_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dunning_attempts_subscription ON dunning_attempts(subscription_id);
CREATE INDEX IF NOT EXISTS idx_dunning_attempts_due ON dunning_attempts(status, next_retry_at) WHERE status = 'PENDING';

ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS dunning_status VARCHAR(50) DEFAULT 'NORMAL';
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS dunning_started_at TIMESTAMPTZ;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS dunning_next_action_at TIMESTAMPTZ;
