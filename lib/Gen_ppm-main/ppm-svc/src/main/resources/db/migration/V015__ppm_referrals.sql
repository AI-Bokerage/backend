-- ── PPM Promotion Engine Phase 2: Referral & PromotionSource ─────────────────

-- Add source column to promotions (NOT NULL, defaults 'normal' — never NULL)
ALTER TABLE ppm_promotions ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'normal';
UPDATE ppm_promotions SET source = 'normal' WHERE source IS NULL; -- backfill (defensive; default already applied)

-- Referral programs
CREATE TABLE ppm_referral_programs (
    id                            UUID          NOT NULL DEFAULT gen_random_uuid(),
    name                          VARCHAR(255)  NOT NULL,
    description                   TEXT,
    referrer_reward_promotion_id  UUID          NOT NULL,
    referred_reward_promotion_id  UUID          NOT NULL,
    status                        VARCHAR(32)   NOT NULL DEFAULT 'active',
    max_referrals_per_referrer    INTEGER,
    version                       BIGINT        NOT NULL DEFAULT 0,
    created_at                    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by                    UUID          NOT NULL,
    updated_by                    UUID          NOT NULL,
    deleted_at                    TIMESTAMPTZ,
    CONSTRAINT pk_ppm_referral_programs PRIMARY KEY (id),
    CONSTRAINT fk_ppm_rp_referrer_reward FOREIGN KEY (referrer_reward_promotion_id) REFERENCES ppm_promotions(id),
    CONSTRAINT fk_ppm_rp_referred_reward FOREIGN KEY (referred_reward_promotion_id) REFERENCES ppm_promotions(id)
);

-- Referral codes (one per referrer per program)
CREATE TABLE ppm_referral_codes (
    id                   UUID          NOT NULL DEFAULT gen_random_uuid(),
    code                 VARCHAR(64)   NOT NULL,
    referral_program_id  UUID          NOT NULL,
    referrer_customer_id VARCHAR(128)  NOT NULL,
    status               VARCHAR(32)   NOT NULL DEFAULT 'active',
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by           UUID          NOT NULL,
    updated_by           UUID          NOT NULL,
    deleted_at           TIMESTAMPTZ,
    CONSTRAINT pk_ppm_referral_codes PRIMARY KEY (id),
    CONSTRAINT fk_ppm_rc_program FOREIGN KEY (referral_program_id) REFERENCES ppm_referral_programs(id),
    CONSTRAINT chk_ppm_rc_code_upper CHECK (code = upper(code) AND length(code) >= 3)
);
CREATE UNIQUE INDEX uq_ppm_referral_codes_code ON ppm_referral_codes (code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_ppm_referral_codes_program_referrer
    ON ppm_referral_codes (referral_program_id, referrer_customer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_ppm_referral_codes_program ON ppm_referral_codes (referral_program_id);

-- Referral events (one per code + referred customer)
CREATE TABLE ppm_referral_events (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    referral_code_id      UUID          NOT NULL,
    referred_customer_id  VARCHAR(128)  NOT NULL,
    status                VARCHAR(32)   NOT NULL DEFAULT 'pending',
    converted_at          TIMESTAMPTZ,
    reward_granted_at     TIMESTAMPTZ,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by            UUID,
    updated_by            UUID,
    deleted_at            TIMESTAMPTZ,
    CONSTRAINT pk_ppm_referral_events PRIMARY KEY (id),
    CONSTRAINT fk_ppm_re_code FOREIGN KEY (referral_code_id) REFERENCES ppm_referral_codes(id)
);
CREATE UNIQUE INDEX uq_ppm_referral_events_code_customer
    ON ppm_referral_events (referral_code_id, referred_customer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_ppm_referral_events_code   ON ppm_referral_events (referral_code_id);
CREATE INDEX idx_ppm_referral_events_status ON ppm_referral_events (status) WHERE deleted_at IS NULL;
