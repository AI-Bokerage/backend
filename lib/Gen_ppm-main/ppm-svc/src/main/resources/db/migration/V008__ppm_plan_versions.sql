-- ============================================================
-- V008 — ppm_plan_versions table
-- ============================================================
-- Stores immutable historical version snapshots for subscription
-- plans.  Each version represents a point-in-time configuration
-- of a plan that may be referenced by active subscriptions.
--
-- Design decisions:
--   * Business key: (plan_id, version_no).
--     Enforced via partial unique index (WHERE deleted_at IS NULL)
--     so a soft-deleted version_no can be reused.
--   * (plan_id, effective_from) is also unique among active rows:
--     two versions of the same plan cannot go live on the same date.
--   * effective_to is nullable — a null value means the version
--     has no scheduled end date (open-ended or still current).
--   * Snapshotting strategy (whether versions copy pricing,
--     modules, entitlements) is deferred to Phase 2.  This table
--     is intentionally minimal.
--   * Soft-delete mirrors all other PPM catalog patterns.
-- ============================================================

CREATE TABLE ppm_plan_versions (

    -- ── Identity ──────────────────────────────────────────
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- ── Relationship ──────────────────────────────────────
    plan_id         UUID            NOT NULL,

    -- ── Version identity ──────────────────────────────────
    version_no      INTEGER         NOT NULL,

    -- ── Effective date range ──────────────────────────────
    effective_from  DATE            NOT NULL,
    effective_to    DATE,

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

    CONSTRAINT pk_ppm_plan_versions PRIMARY KEY (id),

    CONSTRAINT fk_ppm_plan_versions_plan
        FOREIGN KEY (plan_id) REFERENCES ppm_plans(id)
);

-- ── Unique constraints ────────────────────────────────────────────────────────
-- Partial unique index on (plan_id, version_no): one version number per plan
-- among active rows.  Allows reuse after soft-delete.
CREATE UNIQUE INDEX uq_ppm_plan_versions_plan_version
    ON ppm_plan_versions (plan_id, version_no)
    WHERE deleted_at IS NULL;

-- Partial unique index on (plan_id, effective_from): two versions of the same
-- plan cannot become effective on the same date.
CREATE UNIQUE INDEX uq_ppm_plan_versions_effective_from
    ON ppm_plan_versions (plan_id, effective_from)
    WHERE deleted_at IS NULL;

-- ── Indexes ───────────────────────────────────────────────────────────────────
CREATE INDEX idx_ppm_plan_versions_plan_id
    ON ppm_plan_versions (plan_id);

CREATE INDEX idx_ppm_plan_versions_effective_from
    ON ppm_plan_versions (effective_from);

CREATE INDEX idx_ppm_plan_versions_active
    ON ppm_plan_versions (active)
    WHERE deleted_at IS NULL;
