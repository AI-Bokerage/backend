-- ============================================================
-- V002 — ppm_modules table
-- ============================================================
-- Stores the platform capability module catalog.
-- Modules are platform-wide records — no tenant_id column.
-- code is stored as the stable wire value (e.g. 'lead_management'),
-- not the Java enum constant name, via ModuleCodeConverter.
-- ============================================================

CREATE TABLE ppm_modules (

    -- ── Identity ──────────────────────────────────────────
    id              UUID            NOT NULL,

    -- ── Module identity ───────────────────────────────────
    code            VARCHAR(100)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    description     TEXT,

    -- ── Lifecycle ─────────────────────────────────────────
    active          BOOLEAN         NOT NULL DEFAULT TRUE,

    -- ── Optimistic locking ────────────────────────────────
    version         BIGINT          NOT NULL DEFAULT 0,

    -- ── Audit ─────────────────────────────────────────────
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    created_by      UUID,
    updated_by      UUID,

    -- ── Soft delete ───────────────────────────────────────
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT pk_ppm_modules PRIMARY KEY (id)
);

-- ── Constraints ───────────────────────────────────────────────────────────────
-- Partial unique index: code must be unique only among non-deleted rows.
-- A plain UNIQUE constraint would prevent re-using a code after soft-delete.
CREATE UNIQUE INDEX uq_ppm_modules_code ON ppm_modules (code)
    WHERE deleted_at IS NULL;

-- ── Indexes ───────────────────────────────────────────────────────────────────
-- Partial index on active modules — list APIs typically filter active = true.
CREATE INDEX idx_ppm_modules_active ON ppm_modules (active)
    WHERE deleted_at IS NULL;
