-- ============================================================
-- V005 — ppm_plan_modules table
-- ============================================================
-- Stores the many-to-many relationship between subscription plans
-- and platform capability modules.
--
-- Design decisions:
--   * UUID foreign keys only — slugs and codes are never stored
--     here (API-layer identifiers only).
--   * No soft-delete — these are pure join records; hard deletes
--     are appropriate when a module is removed from a plan.
--   * No updated_at / updated_by — mappings are immutable after
--     creation; the only mutation is deletion.
--   * Composite unique index prevents duplicate assignments.
-- ============================================================

CREATE TABLE ppm_plan_modules (

    -- ── Identity ──────────────────────────────────────────
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),

    -- ── Relationship ──────────────────────────────────────
    plan_id     UUID            NOT NULL,
    module_id   UUID            NOT NULL,

    -- ── Optimistic locking ────────────────────────────────
    version     BIGINT          NOT NULL DEFAULT 0,

    -- ── Audit (creation only — these rows are immutable) ──
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by  UUID,

    CONSTRAINT pk_ppm_plan_modules PRIMARY KEY (id),

    CONSTRAINT fk_ppm_plan_modules_plan
        FOREIGN KEY (plan_id) REFERENCES ppm_plans(id),

    CONSTRAINT fk_ppm_plan_modules_module
        FOREIGN KEY (module_id) REFERENCES ppm_modules(id)
);

-- ── Constraints ───────────────────────────────────────────────────────────────
-- Prevent assigning the same module to the same plan twice.
CREATE UNIQUE INDEX uq_ppm_plan_modules_plan_module
    ON ppm_plan_modules (plan_id, module_id);

-- ── Indexes ───────────────────────────────────────────────────────────────────
-- Support "list all modules for plan X" queries efficiently.
CREATE INDEX idx_ppm_plan_modules_plan_id
    ON ppm_plan_modules (plan_id);

-- Support "list all plans that include module Y" queries efficiently.
CREATE INDEX idx_ppm_plan_modules_module_id
    ON ppm_plan_modules (module_id);
