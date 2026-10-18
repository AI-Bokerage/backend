-- Phase 4 Module A: Invoice Domain Foundation
-- Flyway Migration V5

-- ============================================================
-- TABLE: platform_invoices
-- The aggregate root representing a billing invoice.
-- ============================================================
CREATE TABLE IF NOT EXISTS platform_invoices (
    id                      UUID          NOT NULL PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    subscription_id         UUID          NOT NULL REFERENCES subscriptions(id),
    invoice_number          VARCHAR(100)  NOT NULL,
    status                  VARCHAR(50)   NOT NULL,
    amount_due              BIGINT        NOT NULL,
    amount_paid             BIGINT        NOT NULL,
    currency                VARCHAR(10)   NOT NULL,
    period_start            TIMESTAMPTZ   NOT NULL,
    period_end              TIMESTAMPTZ   NOT NULL,
    due_date                DATE          NOT NULL,
    paid_at                 TIMESTAMPTZ   NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                 BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_platform_invoices_invoice_number UNIQUE (invoice_number)
);

-- Indexes for performance and quick lookup
CREATE INDEX IF NOT EXISTS idx_platform_invoices_tenant_id ON platform_invoices(tenant_id);
CREATE INDEX IF NOT EXISTS idx_platform_invoices_subscription_id ON platform_invoices(subscription_id);
CREATE INDEX IF NOT EXISTS idx_platform_invoices_invoice_number ON platform_invoices(invoice_number);
CREATE INDEX IF NOT EXISTS idx_platform_invoices_status ON platform_invoices(status);
CREATE INDEX IF NOT EXISTS idx_platform_invoices_period_start ON platform_invoices(period_start);
CREATE INDEX IF NOT EXISTS idx_platform_invoices_period_end ON platform_invoices(period_end);

-- ============================================================
-- TABLE: invoice_line_items
-- Specific line items belonging to a platform invoice.
-- ============================================================
CREATE TABLE IF NOT EXISTS invoice_line_items (
    id                      UUID          NOT NULL PRIMARY KEY,
    invoice_id              UUID          NOT NULL REFERENCES platform_invoices(id) ON DELETE CASCADE,
    item_type               VARCHAR(50)   NOT NULL,
    description             VARCHAR(255)  NOT NULL,
    quantity                INTEGER       NOT NULL,
    unit_amount_minor       BIGINT        NOT NULL,
    amount_minor            BIGINT        NOT NULL,
    metadata                JSONB         NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Index for parent reference joins
CREATE INDEX IF NOT EXISTS idx_invoice_line_items_invoice_id ON invoice_line_items(invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_line_items_item_type ON invoice_line_items(item_type);
