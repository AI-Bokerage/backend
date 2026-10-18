-- ── PPM Promotion & Coupon Engine Phase 0 ────────────────────────────────────
--
-- ppm_promotions — offer metadata + a single polymorphic action + validity
--                  window + status. Coexists with legacy ppm_promo_codes;
--                  no data migration between the two.
-- ppm_coupons    — redemption codes pointing to a promotion by FK.

CREATE TABLE ppm_promotions (
    id             UUID          NOT NULL DEFAULT gen_random_uuid(),
    name           VARCHAR(255)  NOT NULL,
    description    TEXT,
    action_payload JSONB         NOT NULL,
    valid_from     DATE          NOT NULL,
    valid_until    DATE          NOT NULL,
    status         VARCHAR(32)   NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     UUID          NOT NULL,
    updated_by     UUID          NOT NULL,
    deleted_at     TIMESTAMPTZ,
    CONSTRAINT pk_ppm_promotions PRIMARY KEY (id)
);
CREATE INDEX idx_ppm_promotions_status ON ppm_promotions (status) WHERE deleted_at IS NULL;

CREATE TABLE ppm_coupons (
    id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    code         VARCHAR(64)   NOT NULL,
    promotion_id UUID          NOT NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    version      BIGINT        NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by   UUID          NOT NULL,
    updated_by   UUID          NOT NULL,
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT pk_ppm_coupons PRIMARY KEY (id),
    CONSTRAINT fk_ppm_coupons_promotion
        FOREIGN KEY (promotion_id) REFERENCES ppm_promotions(id),
    CONSTRAINT chk_ppm_coupons_code_upper CHECK (code = upper(code) AND length(code) >= 3)
);
CREATE UNIQUE INDEX uq_ppm_coupons_code ON ppm_coupons (code) WHERE deleted_at IS NULL;
CREATE INDEX idx_ppm_coupons_promotion_id ON ppm_coupons (promotion_id);
