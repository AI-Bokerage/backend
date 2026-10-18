-- V19: Refund request tracking — keeps provider refund reference safe even if
-- local accounting (credit note, ledger) fails. Recovery scheduler uses this table.
CREATE TABLE IF NOT EXISTS refund_requests (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    invoice_id              UUID NOT NULL,
    payment_id              UUID,
    requested_amount_minor  BIGINT NOT NULL,
    provider                VARCHAR(50),
    provider_refund_id      VARCHAR(255),
    status                  VARCHAR(50) NOT NULL,
    credit_note_id          UUID,
    failure_reason          TEXT,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_refund_requests_invoice     ON refund_requests(invoice_id);
CREATE INDEX IF NOT EXISTS idx_refund_requests_status      ON refund_requests(status);
CREATE INDEX IF NOT EXISTS idx_refund_requests_invoice_pay ON refund_requests(invoice_id, payment_id);
