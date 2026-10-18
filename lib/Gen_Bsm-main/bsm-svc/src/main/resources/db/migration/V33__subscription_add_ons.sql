-- C4: PPM Add-On Billing — subscription add-on assignments.
--
-- Each row records a PPM add-on that has been purchased and attached to a
-- subscription.  The price is locked at purchase time (grandfathering invariant):
-- renewals always bill ppm_resolved_price_minor, never re-querying PPM.
--
-- active = false means the add-on has been removed and will not appear in
-- future renewal invoices.  The row is retained for audit purposes.
--
-- Partial unique index: at most one ACTIVE row per (subscription_id, ppm_add_on_id)
-- prevents duplicate purchases while allowing re-purchase after removal.

CREATE TABLE IF NOT EXISTS subscription_add_ons (
    id                          UUID        PRIMARY KEY,
    subscription_id             UUID        NOT NULL REFERENCES subscriptions(id),
    tenant_id                   UUID        NOT NULL,
    ppm_add_on_id               UUID        NOT NULL,
    ppm_add_on_price_id         UUID        NOT NULL,
    ppm_resolved_price_minor    BIGINT      NOT NULL,
    active                      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL,
    created_by                  VARCHAR(128)
);

CREATE UNIQUE INDEX idx_sub_addon_unique_active
    ON subscription_add_ons(subscription_id, ppm_add_on_id)
    WHERE active = TRUE;

CREATE INDEX idx_sub_addon_subscription ON subscription_add_ons(subscription_id);
CREATE INDEX idx_sub_addon_tenant       ON subscription_add_ons(tenant_id);
