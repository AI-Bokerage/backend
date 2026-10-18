-- V22: Enforce uniqueness on payments.external_payment_id (non-null only).
-- Stripe/Razorpay session IDs and payment intent IDs are globally unique;
-- two Payment rows with the same external ID would confuse webhook reconciliation.
-- Replaces the plain index from V14 with a unique index.
DROP INDEX IF EXISTS idx_payments_external;
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_external_payment_id
    ON payments(external_payment_id)
    WHERE external_payment_id IS NOT NULL;
