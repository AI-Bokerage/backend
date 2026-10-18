-- ── PPM Promotion Engine Phase 1: Conditions & Redemption Ledger ─────────────
--
-- Adds the conditions slot + per-user usage cap to ppm_promotions, and a new
-- append-only ppm_promotion_redemptions ledger for per-user usage tracking.

ALTER TABLE ppm_promotions ADD COLUMN conditions_payload JSONB NOT NULL DEFAULT '[]';
ALTER TABLE ppm_promotions ADD COLUMN usage_cap_per_user INTEGER;

-- Redemption ledger. Audit columns (version/updated_at/created_by/updated_by/
-- deleted_at) follow the same JpaBaseEntity convention as every other PPM
-- table, even though this ledger is append-only and never soft-deleted.
CREATE TABLE ppm_promotion_redemptions (
    id               UUID          NOT NULL DEFAULT gen_random_uuid(),
    promotion_id     UUID          NOT NULL,
    customer_id      VARCHAR(128)  NOT NULL,
    plan_id          UUID          NOT NULL,
    redeemed_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    action_payload   JSONB         NOT NULL,
    discount_amount  NUMERIC(19,4) NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       UUID,
    updated_by       UUID,
    deleted_at       TIMESTAMPTZ,
    CONSTRAINT pk_ppm_promotion_redemptions PRIMARY KEY (id),
    CONSTRAINT fk_ppm_promotion_redemptions_promotion
        FOREIGN KEY (promotion_id) REFERENCES ppm_promotions(id)
);
CREATE INDEX idx_ppm_redemptions_promotion_customer
    ON ppm_promotion_redemptions (promotion_id, customer_id);
CREATE INDEX idx_ppm_redemptions_customer
    ON ppm_promotion_redemptions (customer_id);
