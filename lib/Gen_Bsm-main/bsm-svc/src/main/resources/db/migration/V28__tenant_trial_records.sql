-- V28: One trial per tenant lifetime enforcement
--
-- A row in this table means the tenant has consumed their free trial.
-- The table is insert-only — rows are never deleted or updated.
-- PRIMARY KEY (tenant_id) is the database-level concurrency guard:
--   two concurrent transactions that both pass the application-level check
--   and both attempt INSERT will result in one succeeding and the other
--   receiving a primary-key conflict, which rolls back the losing transaction.

CREATE TABLE IF NOT EXISTS tenant_trial_records (
    tenant_id         UUID         PRIMARY KEY,
    subscription_id   UUID         NOT NULL,
    trial_consumed_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_tenant_trial_records_subscription
        FOREIGN KEY (subscription_id) REFERENCES subscriptions (id)
);

-- Backfill: any tenant that has ever had a subscription with trial_ends_at IS NOT NULL
-- has already consumed their lifetime trial.  We anchor to the earliest such row so
-- trial_consumed_at reflects when the tenant first received the trial, not a later
-- duplicate.  ON CONFLICT DO NOTHING makes this re-runnable.
INSERT INTO tenant_trial_records (tenant_id, subscription_id, trial_consumed_at)
SELECT DISTINCT ON (tenant_id)
    tenant_id,
    id                AS subscription_id,
    created_at        AS trial_consumed_at
FROM subscriptions
WHERE trial_ends_at IS NOT NULL
ORDER BY tenant_id, created_at ASC
ON CONFLICT (tenant_id) DO NOTHING;
