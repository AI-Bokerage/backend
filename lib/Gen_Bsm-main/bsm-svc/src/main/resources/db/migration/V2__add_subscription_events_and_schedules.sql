CREATE TABLE IF NOT EXISTS subscription_events (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_subscription_events_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
);

CREATE TABLE IF NOT EXISTS subscription_schedules (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    target_plan_version_id UUID,
    effective_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_subscription_schedules_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT fk_subscription_schedules_target_plan_version FOREIGN KEY (target_plan_version_id) REFERENCES plan_versions (id)
);

CREATE INDEX IF NOT EXISTS idx_subscription_events_subscription_id ON subscription_events (subscription_id);
CREATE INDEX IF NOT EXISTS idx_subscription_events_tenant_id ON subscription_events (tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscription_events_event_type ON subscription_events (event_type);
CREATE INDEX IF NOT EXISTS idx_subscription_events_occurred_at ON subscription_events (occurred_at);

CREATE INDEX IF NOT EXISTS idx_subscription_schedules_subscription_id ON subscription_schedules (subscription_id);
CREATE INDEX IF NOT EXISTS idx_subscription_schedules_tenant_id ON subscription_schedules (tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscription_schedules_action_type ON subscription_schedules (action_type);
CREATE INDEX IF NOT EXISTS idx_subscription_schedules_status ON subscription_schedules (status);
CREATE INDEX IF NOT EXISTS idx_subscription_schedules_effective_at ON subscription_schedules (effective_at);
