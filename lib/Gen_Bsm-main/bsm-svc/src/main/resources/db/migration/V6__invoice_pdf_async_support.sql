-- Add PDF support fields to platform_invoices
ALTER TABLE platform_invoices
  ADD COLUMN IF NOT EXISTS pdf_url TEXT NULL,
  ADD COLUMN IF NOT EXISTS pdf_generated_at TIMESTAMPTZ NULL,
  ADD COLUMN IF NOT EXISTS pdf_generation_status VARCHAR(50) NOT NULL DEFAULT 'PENDING';
