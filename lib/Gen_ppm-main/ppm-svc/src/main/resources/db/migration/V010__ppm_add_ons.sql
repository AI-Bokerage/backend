-- ── PPM-11 Phase 1: Add-On Catalog ───────────────────────────────────────────
--
-- ppm_add_ons          — purchasable add-on catalog (features, quotas, services)
-- ppm_add_on_prices    — versioned, region/currency/cycle pricing for each add-on
-- ppm_plan_add_ons     — add-on availability assignments per plan

-- ── ppm_add_ons ───────────────────────────────────────────────────────────────

CREATE TABLE ppm_add_ons (
    id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    code        VARCHAR(100)    NOT NULL,
    name        VARCHAR(255)    NOT NULL,
    description TEXT,
    type        VARCHAR(50)     NOT NULL,
    active      BOOLEAN         NOT NULL DEFAULT TRUE,
    version     BIGINT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by  UUID            NOT NULL,
    updated_by  UUID            NOT NULL,
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT pk_ppm_add_ons PRIMARY KEY (id)
);

-- Partial unique index: code reuse is allowed after soft-delete
CREATE UNIQUE INDEX uq_ppm_add_ons_code
    ON ppm_add_ons (code)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_ppm_add_ons_active
    ON ppm_add_ons (active)
    WHERE deleted_at IS NULL;

-- ── ppm_add_on_prices ─────────────────────────────────────────────────────────

CREATE TABLE ppm_add_on_prices (
    id             UUID            NOT NULL DEFAULT gen_random_uuid(),
    add_on_id      UUID            NOT NULL,
    cycle          VARCHAR(50)     NOT NULL,
    currency       VARCHAR(10)     NOT NULL,
    region         VARCHAR(50)     NOT NULL,
    amount         NUMERIC(19,4)   NOT NULL,
    tax_inclusive  BOOLEAN         NOT NULL DEFAULT FALSE,
    effective_from DATE            NOT NULL,
    active         BOOLEAN         NOT NULL DEFAULT TRUE,
    version        BIGINT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_by     UUID            NOT NULL,
    updated_by     UUID            NOT NULL,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT pk_ppm_add_on_prices PRIMARY KEY (id),
    CONSTRAINT fk_ppm_add_on_prices_add_on
        FOREIGN KEY (add_on_id) REFERENCES ppm_add_ons(id)
);

-- Partial unique: one active price per (add_on_id, region, currency, cycle, effective_from)
CREATE UNIQUE INDEX uq_ppm_add_on_prices_active
    ON ppm_add_on_prices (add_on_id, region, currency, cycle, effective_from)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_ppm_add_on_prices_add_on_id
    ON ppm_add_on_prices (add_on_id);
CREATE INDEX idx_ppm_add_on_prices_region
    ON ppm_add_on_prices (region);
CREATE INDEX idx_ppm_add_on_prices_currency
    ON ppm_add_on_prices (currency);
CREATE INDEX idx_ppm_add_on_prices_effective_from
    ON ppm_add_on_prices (effective_from);

-- ── ppm_plan_add_ons ──────────────────────────────────────────────────────────

CREATE TABLE ppm_plan_add_ons (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    plan_id     UUID        NOT NULL,
    add_on_id   UUID        NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  UUID        NOT NULL,
    CONSTRAINT pk_ppm_plan_add_ons PRIMARY KEY (id),
    CONSTRAINT fk_ppm_plan_add_ons_plan
        FOREIGN KEY (plan_id) REFERENCES ppm_plans(id),
    CONSTRAINT fk_ppm_plan_add_ons_add_on
        FOREIGN KEY (add_on_id) REFERENCES ppm_add_ons(id),
    CONSTRAINT uq_ppm_plan_add_on
        UNIQUE (plan_id, add_on_id)
);

CREATE INDEX idx_ppm_plan_add_ons_plan_id
    ON ppm_plan_add_ons (plan_id);
CREATE INDEX idx_ppm_plan_add_ons_add_on_id
    ON ppm_plan_add_ons (add_on_id);
