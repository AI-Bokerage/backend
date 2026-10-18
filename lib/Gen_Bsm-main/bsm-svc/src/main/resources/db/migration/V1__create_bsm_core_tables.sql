CREATE TABLE IF NOT EXISTS plans (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    tier VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_plans_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS plan_versions (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL,
    version_no INTEGER NOT NULL,
    monthly_price_minor BIGINT NOT NULL,
    yearly_price_minor BIGINT NOT NULL,
    max_internal_users INTEGER NOT NULL,
    max_client_users INTEGER NOT NULL,
    max_active_projects INTEGER NOT NULL,
    storage_quota_bytes BIGINT NOT NULL,
    custom_domain_enabled BOOLEAN NOT NULL,
    sso_enabled BOOLEAN NOT NULL,
    priority_support BOOLEAN NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_plan_versions_plan FOREIGN KEY (plan_id) REFERENCES plans (id),
    CONSTRAINT uq_plan_versions_plan_version UNIQUE (plan_id, version_no)
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    plan_version_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    billing_cycle VARCHAR(32) NOT NULL,
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end TIMESTAMPTZ NOT NULL,
    trial_ends_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_subscriptions_plan_version FOREIGN KEY (plan_version_id) REFERENCES plan_versions (id)
);

CREATE TABLE IF NOT EXISTS subscription_history (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    action VARCHAR(64) NOT NULL,
    from_plan_version_id UUID,
    to_plan_version_id UUID,
    reason VARCHAR(512),
    performed_by VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_subscription_history_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id),
    CONSTRAINT fk_subscription_history_from_plan_version FOREIGN KEY (from_plan_version_id) REFERENCES plan_versions (id),
    CONSTRAINT fk_subscription_history_to_plan_version FOREIGN KEY (to_plan_version_id) REFERENCES plan_versions (id)
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_tenant_id ON subscriptions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions (status);
CREATE INDEX IF NOT EXISTS idx_plan_versions_plan_id ON plan_versions (plan_id);
CREATE INDEX IF NOT EXISTS idx_subscription_history_subscription_id ON subscription_history (subscription_id);
CREATE INDEX IF NOT EXISTS idx_subscription_history_tenant_id ON subscription_history (tenant_id);
