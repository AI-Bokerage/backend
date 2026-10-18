-- V29: Financial Integrity Hardening
--
-- 1. Ledger deduplication: partial unique indexes enforce "one INVOICE_PAID and one
--    INVOICE_CREATED per invoice" at the database level.  These are backstop guards —
--    the primary fix is removing duplicate writes at the application layer.  The
--    indexes will surface any regressions immediately rather than silently accumulating
--    duplicate rows.
--
-- 2. Outbox optimistic locking: a version column on bsm_outbox_events enables
--    @Version on BsmOutboxEventEntity so concurrent pod saves on the same row produce
--    an OptimisticLockingFailureException instead of silent overwrites.

-- ── Ledger deduplication ──────────────────────────────────────────────────────────

-- Before adding unique indexes, remove any duplicate INVOICE_PAID / INVOICE_CREATED
-- entries accumulated before Phase 8 (when the double-write bug was present).
-- Keeps the earliest entry per invoice; deletes all later duplicates.

DELETE FROM billing_ledger_entries a
USING billing_ledger_entries b
WHERE a.entry_type = 'INVOICE_PAID'
  AND b.entry_type = 'INVOICE_PAID'
  AND a.invoice_id  = b.invoice_id
  AND a.invoice_id IS NOT NULL
  AND a.created_at  > b.created_at;

DELETE FROM billing_ledger_entries a
USING billing_ledger_entries b
WHERE a.entry_type = 'INVOICE_CREATED'
  AND b.entry_type = 'INVOICE_CREATED'
  AND a.invoice_id  = b.invoice_id
  AND a.invoice_id IS NOT NULL
  AND a.created_at  > b.created_at;

-- At most one INVOICE_PAID entry per invoice.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_one_invoice_paid
    ON billing_ledger_entries (invoice_id)
    WHERE entry_type = 'INVOICE_PAID' AND invoice_id IS NOT NULL;

-- At most one INVOICE_CREATED entry per invoice.
CREATE UNIQUE INDEX IF NOT EXISTS uq_ledger_one_invoice_created
    ON billing_ledger_entries (invoice_id)
    WHERE entry_type = 'INVOICE_CREATED' AND invoice_id IS NOT NULL;

-- ── Outbox optimistic locking ────────────────────────────────────────────────────

ALTER TABLE bsm_outbox_events
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
