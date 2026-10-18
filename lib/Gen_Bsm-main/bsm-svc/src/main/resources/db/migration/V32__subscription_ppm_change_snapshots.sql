-- C3: Subscription plan change history for PPM-backed subscriptions.
--
-- Captures the full before/after PPM snapshot every time a PPM-backed
-- subscription changes plan (upgrade, downgrade, or same-price switch).
-- This table is append-only — rows are never updated or deleted.
--
-- Grandfathering invariant:
--   The LATEST row per subscription_id reflects the subscription's current
--   PPM identifiers. Historical rows are immutable records of past state.
--
-- change_type: UPGRADE | DOWNGRADE | PLAN_SWITCH

CREATE TABLE IF NOT EXISTS subscription_ppm_change_snapshots (
    id                              UUID PRIMARY KEY,
    subscription_id                 UUID NOT NULL REFERENCES subscriptions(id),
    tenant_id                       UUID NOT NULL,
    change_type                     VARCHAR(32) NOT NULL,

    -- Before state (locked at original checkout or prior change)
    from_ppm_plan_id                UUID NOT NULL,
    from_ppm_price_id               UUID NOT NULL,
    from_ppm_plan_version_id        UUID NOT NULL,
    from_ppm_resolved_price_minor   BIGINT NOT NULL,

    -- After state (newly resolved from PPM at change time)
    to_ppm_plan_id                  UUID NOT NULL,
    to_ppm_price_id                 UUID NOT NULL,
    to_ppm_plan_version_id          UUID NOT NULL,
    to_ppm_resolved_price_minor     BIGINT NOT NULL,

    -- Proration details (all in minor units)
    proration_credit_minor          BIGINT NOT NULL DEFAULT 0,
    proration_charge_minor          BIGINT NOT NULL DEFAULT 0,
    proration_net_minor             BIGINT NOT NULL DEFAULT 0,

    -- Invoice generated for upgrade delta (NULL for downgrade or zero proration)
    invoice_id                      UUID NULL,

    -- Audit
    changed_at                      TIMESTAMPTZ NOT NULL,
    changed_by                      VARCHAR(128),
    reason                          VARCHAR(512)
);

CREATE INDEX IF NOT EXISTS idx_sppcs_subscription_id
    ON subscription_ppm_change_snapshots(subscription_id);

CREATE INDEX IF NOT EXISTS idx_sppcs_tenant_id
    ON subscription_ppm_change_snapshots(tenant_id);
