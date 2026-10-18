-- V20: Invoice source tracking — records how an invoice was generated.
-- Nullable to preserve compatibility with existing rows (treated as MANUAL).
ALTER TABLE platform_invoices
    ADD COLUMN IF NOT EXISTS source VARCHAR(50) DEFAULT 'MANUAL';

CREATE INDEX IF NOT EXISTS idx_invoices_source ON platform_invoices(source);
