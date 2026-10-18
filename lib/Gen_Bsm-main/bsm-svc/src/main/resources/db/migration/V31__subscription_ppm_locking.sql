-- C2: Persist PPM catalog identifiers on subscriptions for grandfathering.
--
-- All four columns are NULLABLE so existing rows and the existing subscription
-- creation path (/api/v1/bsm/subscriptions) are completely unaffected.
-- Only PPM-backed checkouts (/api/v1/bsm/checkout/ppm-initiate) populate them.
--
-- ppm_plan_id          — PPM plan UUID at checkout time
-- ppm_price_id         — PPM price UUID resolved at checkout time
-- ppm_plan_version_id  — PPM plan version UUID locked at checkout time
-- ppm_resolved_price_minor — PPM-resolved amount in minor units (used by renewal)
--
-- Once written, these columns must never be updated automatically by any
-- scheduler, renewal job, or migration.

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS ppm_plan_id              UUID    NULL,
    ADD COLUMN IF NOT EXISTS ppm_price_id             UUID    NULL,
    ADD COLUMN IF NOT EXISTS ppm_plan_version_id      UUID    NULL,
    ADD COLUMN IF NOT EXISTS ppm_resolved_price_minor BIGINT  NULL;
