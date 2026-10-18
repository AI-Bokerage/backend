-- ── PPM-07 Phase 1: Promo Code Catalog ───────────────────────────────────────
--
-- ppm_promo_codes  — core promo code rows with discount definitions, usage
--                    limits, and active windows.
-- ppm_promo_code_plans — optional plan restrictions for a promo code.

-- ── ppm_promo_codes ───────────────────────────────────────────────────────────

CREATE TABLE ppm_promo_codes (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    code            VARCHAR(64)     NOT NULL,
    discount_type   VARCHAR(32)     NOT NULL,
    value           NUMERIC(19,4)   NOT NULL,
    valid_from      DATE            NOT NULL,
    valid_until     DATE            NOT NULL,
    usage_cap       INTEGER,
    usage_count     INTEGER         NOT NULL DEFAULT 0,
    first_time_only BOOLEAN         NOT NULL DEFAULT FALSE,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      UUID            NOT NULL,
    updated_by      UUID            NOT NULL,
    deleted_at      TIMESTAMPTZ,
    CONSTRAINT pk_ppm_promo_codes PRIMARY KEY (id)
);

-- Partial unique index: code reuse is allowed after soft-delete
CREATE UNIQUE INDEX uq_ppm_promo_codes_code
    ON ppm_promo_codes (code)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_ppm_promo_codes_valid_from   ON ppm_promo_codes (valid_from);
CREATE INDEX idx_ppm_promo_codes_valid_until  ON ppm_promo_codes (valid_until);
CREATE INDEX idx_ppm_promo_codes_active       ON ppm_promo_codes (active) WHERE deleted_at IS NULL;

-- ── ppm_promo_code_plans ──────────────────────────────────────────────────────

CREATE TABLE ppm_promo_code_plans (
    id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    promo_code_id   UUID            NOT NULL,
    plan_id         UUID            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by      UUID            NOT NULL,
    CONSTRAINT pk_ppm_promo_code_plans PRIMARY KEY (id),
    CONSTRAINT fk_ppm_promo_code_plans_promo_code
        FOREIGN KEY (promo_code_id) REFERENCES ppm_promo_codes(id),
    CONSTRAINT fk_ppm_promo_code_plans_plan
        FOREIGN KEY (plan_id) REFERENCES ppm_plans(id)
);

CREATE UNIQUE INDEX uq_ppm_promo_code_plan
    ON ppm_promo_code_plans (promo_code_id, plan_id);

CREATE INDEX idx_ppm_promo_code_plans_promo_code_id ON ppm_promo_code_plans (promo_code_id);
CREATE INDEX idx_ppm_promo_code_plans_plan_id       ON ppm_promo_code_plans (plan_id);
