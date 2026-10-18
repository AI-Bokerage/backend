-- Flyway V8: create billing_ledger_entries table
CREATE TABLE IF NOT EXISTS billing_ledger_entries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    subscription_id UUID NOT NULL,
    invoice_id UUID,
    credit_note_id UUID,
    entry_type VARCHAR(50) NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    description VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_billing_ledger_tenant ON billing_ledger_entries(tenant_id);
CREATE INDEX IF NOT EXISTS idx_billing_ledger_invoice ON billing_ledger_entries(invoice_id);
CREATE INDEX IF NOT EXISTS idx_billing_ledger_credit_note ON billing_ledger_entries(credit_note_id);
CREATE INDEX IF NOT EXISTS idx_billing_ledger_subscription ON billing_ledger_entries(subscription_id);
