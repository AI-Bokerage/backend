-- V24: Narrow the billing-period uniqueness constraint to recurring invoice sources only.
--
-- V17 added a FULL unique constraint on (subscription_id, period_start, period_end) for ALL sources.
-- That blocked UPGRADE / DOWNGRADE / ADJUSTMENT invoices from existing alongside a MANUAL or RENEWAL
-- invoice for the same billing period, which is a valid business scenario.
--
-- New rule:
--   For RECURRING sources (MANUAL, SUBSCRIPTION_RENEWAL) only one invoice may exist per
--   (subscription_id, period_start, period_end).  Non-recurring sources (UPGRADE, DOWNGRADE,
--   ADJUSTMENT) are NOT subject to this constraint.

-- Drop the old all-sources constraint from V17
ALTER TABLE platform_invoices
    DROP CONSTRAINT IF EXISTS uq_invoice_subscription_billing_period;

-- Add a partial unique index covering recurring sources only.
-- PostgreSQL enforces uniqueness across all rows that satisfy the WHERE predicate.
CREATE UNIQUE INDEX IF NOT EXISTS uq_recurring_invoice_per_billing_period
    ON platform_invoices(subscription_id, period_start, period_end)
    WHERE source IN ('MANUAL', 'SUBSCRIPTION_RENEWAL');
