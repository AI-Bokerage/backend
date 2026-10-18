-- ============================================================
-- V007 — ppm_plan_prices table
-- ============================================================
-- Stores region-aware, cycle-specific pricing entries for
-- subscription plans.
--
-- Design decisions:
--   * One row = one billing cycle.  Monthly and annual prices
--     are stored as separate rows; this makes future versioning,
--     grandfathering, and effective-dated pricing straightforward.
--   * Business key: (plan_id, region, currency, cycle, effective_from).
--     Enforced via partial unique index (WHERE deleted_at IS NULL)
--     so a soft-deleted key can be reused by a new price row.
--   * cycle is stored as a VARCHAR wire value ("monthly", "annual")
--     via BillingCycleConverter — never the Java constant name.
--   * currency is VARCHAR(3) (ISO 4217).
--   * amount is NUMERIC(19,4) — no floating-point ambiguity.
--   * effective_from is DATE — price activation is date-precision only.
--   * Soft-delete mirrors the entitlement catalog pattern.
-- ============================================================

CREATE TABLE ppm_plan_prices (

    -- ── Identity ──────────────────────────────────────────
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- ── Relationship ──────────────────────────────────────
    plan_id         UUID            NOT NULL,

    -- ── Pricing dimensions ────────────────────────────────
    cycle           VARCHAR(32)     NOT NULL,
    currency        VARCHAR(3)      NOT NULL,
    region          VARCHAR(32)     NOT NULL,

    -- ── Price value ───────────────────────────────────────
    amount          NUMERIC(19,4)   NOT NULL,
    tax_inclusive   BOOLEAN         NOT NULL DEFAULT false,
    effective_from  DATE            NOT NULL,

    -- ── Lifecycle ─────────────────────────────────────────
    active          BOOLEAN         NOT NULL DEFAULT true,

    -- ── Optimistic locking ────────────────────────────────
    version         BIGINT          NOT NULL DEFAULT 0,

    -- ── Audit ─────────────────────────────────────────────
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_by      UUID,

    -- ── Soft-delete ───────────────────────────────────────
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT pk_ppm_plan_prices PRIMARY KEY (id),

    CONSTRAINT fk_ppm_plan_prices_plan
        FOREIGN KEY (plan_id) REFERENCES ppm_plans(id)
);

-- ── Constraints ───────────────────────────────────────────────────────────────
-- Partial unique index on the business key: allows re-use of a pricing key after
-- soft-delete (e.g. re-price STARTER/INR/MONTHLY from 2025-01-01 after archiving).
CREATE UNIQUE INDEX uq_ppm_plan_prices_active
    ON ppm_plan_prices (plan_id, region, currency, cycle, effective_from)
    WHERE deleted_at IS NULL;

-- ── Indexes ───────────────────────────────────────────────────────────────────
-- Support "all prices for plan X" queries.
CREATE INDEX idx_ppm_plan_prices_plan_id
    ON ppm_plan_prices (plan_id);

-- Support "prices available in region X" catalog queries.
CREATE INDEX idx_ppm_plan_prices_region
    ON ppm_plan_prices (region)
    WHERE deleted_at IS NULL;

-- Support "prices in currency X" catalog queries.
CREATE INDEX idx_ppm_plan_prices_currency
    ON ppm_plan_prices (currency)
    WHERE deleted_at IS NULL;

-- Support effective-date range queries (e.g. price active as of date D).
CREATE INDEX idx_ppm_plan_prices_effective_from
    ON ppm_plan_prices (effective_from);
