-- Phase 3: Subscription Commercial Engine
-- Proration Preview, Migration Plans, Subscription Limit Snapshots

-- ============================================================
-- TABLE: proration_previews
-- Captures financial impact previews for plan changes.
-- Append-only; never updated after creation.
-- ============================================================
CREATE TABLE IF NOT EXISTS proration_previews (
    id                      UUID          NOT NULL PRIMARY KEY,
    subscription_id         UUID          NOT NULL REFERENCES subscriptions(id),
    from_plan_version_id    UUID          NOT NULL,
    to_plan_version_id      UUID          NOT NULL,
    proration_mode          VARCHAR(32)   NOT NULL,
    current_plan_credit_minor BIGINT      NOT NULL DEFAULT 0,
    target_plan_charge_minor  BIGINT      NOT NULL DEFAULT 0,
    prorated_amount_minor   BIGINT        NOT NULL,
    currency                VARCHAR(8)    NOT NULL DEFAULT 'INR',
    breakdown               JSONB,
    expires_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_proration_previews_subscription_id
    ON proration_previews (subscription_id);

CREATE INDEX IF NOT EXISTS idx_proration_previews_created_at
    ON proration_previews (created_at);

CREATE INDEX IF NOT EXISTS idx_proration_previews_sub_id_created_at
    ON proration_previews (subscription_id, created_at DESC);

-- ============================================================
-- TABLE: migration_plans
-- Top-level plan to orchestrate resource actions before downgrade.
-- ============================================================
CREATE TABLE IF NOT EXISTS migration_plans (
    id                      UUID          NOT NULL PRIMARY KEY,
    subscription_id         UUID          NOT NULL REFERENCES subscriptions(id),
    tenant_id               UUID          NOT NULL,
    target_plan_version_id  UUID          NOT NULL,
    status                  VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
    created_by              UUID          NOT NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_migration_plans_subscription_id
    ON migration_plans (subscription_id);

CREATE INDEX IF NOT EXISTS idx_migration_plans_tenant_id
    ON migration_plans (tenant_id);

CREATE INDEX IF NOT EXISTS idx_migration_plans_target_plan_version_id
    ON migration_plans (target_plan_version_id);

CREATE INDEX IF NOT EXISTS idx_migration_plans_status
    ON migration_plans (status);

CREATE INDEX IF NOT EXISTS idx_migration_plans_created_at
    ON migration_plans (created_at);

-- ============================================================
-- TABLE: migration_plan_items
-- Individual resource-level action items within a migration plan.
-- ============================================================
CREATE TABLE IF NOT EXISTS migration_plan_items (
    id                      UUID          NOT NULL PRIMARY KEY,
    migration_plan_id       UUID          NOT NULL REFERENCES migration_plans(id),
    resource_type           VARCHAR(32)   NOT NULL,
    resource_id             UUID          NOT NULL,
    action                  VARCHAR(64)   NOT NULL,
    metadata                JSONB,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_migration_plan_items_plan_id
    ON migration_plan_items (migration_plan_id);

CREATE INDEX IF NOT EXISTS idx_migration_plan_items_resource_type
    ON migration_plan_items (resource_type);

CREATE INDEX IF NOT EXISTS idx_migration_plan_items_resource_id
    ON migration_plan_items (resource_id);

-- ============================================================
-- TABLE: subscription_limit_snapshots
-- Point-in-time capture of limits vs usage during downgrade preflight.
-- Append-only audit record.
-- ============================================================
CREATE TABLE IF NOT EXISTS subscription_limit_snapshots (
    id                      UUID          NOT NULL PRIMARY KEY,
    subscription_id         UUID          NOT NULL REFERENCES subscriptions(id),
    plan_version_id         UUID          NOT NULL,
    limits_snapshot         JSONB         NOT NULL,
    usage_snapshot          JSONB         NOT NULL,
    over_limit              BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_limit_snapshots_subscription_id
    ON subscription_limit_snapshots (subscription_id);

CREATE INDEX IF NOT EXISTS idx_limit_snapshots_over_limit
    ON subscription_limit_snapshots (over_limit);

CREATE INDEX IF NOT EXISTS idx_limit_snapshots_created_at
    ON subscription_limit_snapshots (created_at);
