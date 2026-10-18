-- ============================================================
-- V003 — ppm_plans table
-- ============================================================
-- Stores the platform subscription plan catalog.
-- Plans are platform-wide records — no tenant_id column.
-- visibility is stored as the stable wire value (e.g. 'public'),
-- not the Java enum constant name, via PlanVisibilityConverter.
-- slug is immutable after creation and enforced unique by a
-- partial index (allows reuse after soft-delete).
-- ============================================================

CREATE TABLE ppm_plans (

    -- ── Identity ──────────────────────────────────────────
    id              UUID            NOT NULL,

    -- ── Plan identity ─────────────────────────────────────
    code            VARCHAR(50)     NOT NULL,
    slug            VARCHAR(100)    NOT NULL,
    name            VARCHAR(255)    NOT NULL,
    tagline         VARCHAR(500),
    description     TEXT,

    -- ── Catalog metadata ──────────────────────────────────
    visibility      VARCHAR(20)     NOT NULL,
    trial_days      INTEGER         NOT NULL DEFAULT 0,

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

    CONSTRAINT pk_ppm_plans PRIMARY KEY (id)
);

-- ── Constraints ───────────────────────────────────────────────────────────────
-- Partial unique index on code: unique only among non-deleted rows.
-- Allows the same code to be reused after a soft-delete.
CREATE UNIQUE INDEX uq_ppm_plans_code ON ppm_plans (code)
    WHERE deleted_at IS NULL;

-- Partial unique index on slug: same semantics as the code index.
-- Slugs are immutable after creation but can be reclaimed after soft-delete.
CREATE UNIQUE INDEX uq_ppm_plans_slug ON ppm_plans (slug)
    WHERE deleted_at IS NULL;

-- ── Indexes ───────────────────────────────────────────────────────────────────
-- Visibility index — public plan queries filter on visibility frequently.
CREATE INDEX idx_ppm_plans_visibility ON ppm_plans (visibility)
    WHERE deleted_at IS NULL;

-- Active flag index — catalog list APIs typically filter active = true.
CREATE INDEX idx_ppm_plans_active ON ppm_plans (active)
    WHERE deleted_at IS NULL;
