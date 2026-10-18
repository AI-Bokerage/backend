-- FIX 2: Partial unique index — only one non-cancelled subscription per tenant
CREATE UNIQUE INDEX IF NOT EXISTS uq_tenant_active_subscription
    ON subscriptions (tenant_id)
    WHERE status IN ('ACTIVE', 'TRIALING', 'PAUSED');

-- FIX 8: event_version column on subscription_events
ALTER TABLE subscription_events
    ADD COLUMN IF NOT EXISTS event_version INT NOT NULL DEFAULT 1;

-- FIX 9: actor columns on subscription_history
ALTER TABLE subscription_history
    ADD COLUMN IF NOT EXISTS actor_id   UUID,
    ADD COLUMN IF NOT EXISTS actor_type VARCHAR(32);

-- FIX 9: actor columns on subscription_events
ALTER TABLE subscription_events
    ADD COLUMN IF NOT EXISTS actor_id   UUID,
    ADD COLUMN IF NOT EXISTS actor_type VARCHAR(32);

-- FIX 10: composite indexes on subscription_history
CREATE INDEX IF NOT EXISTS idx_subscription_history_sub_id_occurred_at
    ON subscription_history (subscription_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_subscription_history_tenant_id_occurred_at
    ON subscription_history (tenant_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_subscription_history_action_occurred_at
    ON subscription_history (action, occurred_at);

-- FIX 13: schedule audit columns
ALTER TABLE subscription_schedules
    ADD COLUMN IF NOT EXISTS executed_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS executed_by     VARCHAR(128),
    ADD COLUMN IF NOT EXISTS failure_reason  TEXT;
