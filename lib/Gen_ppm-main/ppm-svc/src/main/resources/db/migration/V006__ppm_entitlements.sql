-- ============================================================
-- V006 — ppm_entitlements and ppm_plan_entitlements tables
-- ============================================================
-- The Entitlement Catalog stores reusable limit and permission
-- definitions (e.g. max_internal_users, reporting_enabled).
-- PlanEntitlements assign these definitions to plans with a
-- concrete value (e.g. STARTER → max_internal_users = "10").
--
-- Design decisions:
--   * ppm_entitlements uses soft-delete (deleted_at IS NULL).
--     Entitlement definitions are catalog records; they should
--     never be hard-deleted while plans reference them.
--   * ppm_plan_entitlements uses hard delete.
--     Assignment rows are create-only; the only mutation is
--     removal via DELETE.
--   * No slugs anywhere — the stable identifier is `code`.
--   * `type` is stored as a VARCHAR wire value (e.g. "boolean",
--     "quota", "rate_limit") via EntitlementTypeConverter.
--   * Unique index on ppm_entitlements.code is partial
--     (WHERE deleted_at IS NULL) so a soft-deleted code can be
--     reused by a new entitlement definition.
-- ============================================================

-- ── Table 1: ppm_entitlements ─────────────────────────────────────────────────

CREATE TABLE ppm_entitlements (

    -- ── Identity ──────────────────────────────────────────
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- ── Catalog fields ────────────────────────────────────
    code        VARCHAR(100)    NOT NULL,
    name        VARCHAR(255)    NOT NULL,
    description TEXT,
    type        VARCHAR(32)     NOT NULL,
    active      BOOLEAN         NOT NULL DEFAULT true,

    -- ── Optimistic locking ────────────────────────────────
    version     BIGINT          NOT NULL DEFAULT 0,

    -- ── Audit ─────────────────────────────────────────────
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,

    -- ── Soft-delete ───────────────────────────────────────
    deleted_at  TIMESTAMPTZ,

    CONSTRAINT pk_ppm_entitlements PRIMARY KEY (id)
);

-- Partial unique index: allows re-use of a code after soft-delete.
CREATE UNIQUE INDEX uq_ppm_entitlements_code
    ON ppm_entitlements (code)
    WHERE deleted_at IS NULL;

-- Support active-only catalog queries.
CREATE INDEX idx_ppm_entitlements_active
    ON ppm_entitlements (active)
    WHERE deleted_at IS NULL;

-- ── Table 2: ppm_plan_entitlements ────────────────────────────────────────────

CREATE TABLE ppm_plan_entitlements (

    -- ── Identity ──────────────────────────────────────────
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- ── Relationship ──────────────────────────────────────
    plan_id         UUID            NOT NULL,
    entitlement_id  UUID            NOT NULL,

    -- ── Value assigned to this plan ───────────────────────
    value           VARCHAR(255)    NOT NULL,

    -- ── Optimistic locking ────────────────────────────────
    version         BIGINT          NOT NULL DEFAULT 0,

    -- ── Audit (creation only — rows are immutable after create) ──
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      UUID,

    CONSTRAINT pk_ppm_plan_entitlements PRIMARY KEY (id),

    CONSTRAINT fk_ppm_plan_entitlements_plan
        FOREIGN KEY (plan_id) REFERENCES ppm_plans(id),

    CONSTRAINT fk_ppm_plan_entitlements_entitlement
        FOREIGN KEY (entitlement_id) REFERENCES ppm_entitlements(id)
);

-- Prevent assigning the same entitlement to the same plan twice.
CREATE UNIQUE INDEX uq_ppm_plan_entitlement
    ON ppm_plan_entitlements (plan_id, entitlement_id);

-- Support "list all entitlements for plan X" queries efficiently.
CREATE INDEX idx_ppm_plan_entitlements_plan_id
    ON ppm_plan_entitlements (plan_id);

-- Support "list all plans that use entitlement Y" queries efficiently.
CREATE INDEX idx_ppm_plan_entitlements_entitlement_id
    ON ppm_plan_entitlements (entitlement_id);
