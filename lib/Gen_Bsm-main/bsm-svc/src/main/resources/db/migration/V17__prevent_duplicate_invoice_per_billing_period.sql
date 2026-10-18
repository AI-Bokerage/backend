-- V17: Prevent two invoices for the same subscription covering the same billing period.
-- This is the DB-level safety net; the application layer guards first.
-- Remove any duplicates before adding the constraint (keeps the earliest invoice per group).

-- Step 1: delete line items of duplicate invoices first (FK prevents deleting invoices directly).
DELETE FROM invoice_line_items
WHERE invoice_id NOT IN (
    SELECT DISTINCT ON (subscription_id, period_start, period_end) id
    FROM platform_invoices
    ORDER BY subscription_id, period_start, period_end, created_at ASC
);

-- Step 2: now delete the duplicate invoice rows.
DELETE FROM platform_invoices
WHERE id NOT IN (
    SELECT DISTINCT ON (subscription_id, period_start, period_end) id
    FROM platform_invoices
    ORDER BY subscription_id, period_start, period_end, created_at ASC
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'uq_invoice_subscription_billing_period'
          AND table_name = 'platform_invoices'
    ) THEN
        ALTER TABLE platform_invoices
            ADD CONSTRAINT uq_invoice_subscription_billing_period
            UNIQUE (subscription_id, period_start, period_end);
    END IF;
END $$;
