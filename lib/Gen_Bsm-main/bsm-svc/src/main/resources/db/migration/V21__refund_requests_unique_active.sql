-- V21: DB-level guard against concurrent duplicate provider refunds.
-- Two requests for the same (invoice, payment, amount) that are both non-FAILED
-- cannot coexist. FAILED records are excluded so retries after provider failure are allowed.
-- This backs the application-level findActiveByInvoiceAndPaymentAndAmount check.
CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_requests_active_dedup
    ON refund_requests (invoice_id, payment_id, requested_amount_minor)
    WHERE status <> 'FAILED';
