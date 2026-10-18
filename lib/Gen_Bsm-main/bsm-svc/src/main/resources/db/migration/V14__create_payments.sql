-- Flyway V14: Payment records linking invoices to provider payment sessions/intents
CREATE TABLE IF NOT EXISTS payments (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    invoice_id           UUID NOT NULL,
    payment_provider     VARCHAR(50) NOT NULL,
    external_payment_id  VARCHAR(255),
    external_charge_id   VARCHAR(255),
    status               VARCHAR(50) NOT NULL,
    amount_minor         BIGINT NOT NULL,
    currency             VARCHAR(10) NOT NULL,
    failure_reason       TEXT,
    created_at           TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_payments_tenant ON payments(tenant_id);
CREATE INDEX IF NOT EXISTS idx_payments_invoice ON payments(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payments_external ON payments(external_payment_id) WHERE external_payment_id IS NOT NULL;
