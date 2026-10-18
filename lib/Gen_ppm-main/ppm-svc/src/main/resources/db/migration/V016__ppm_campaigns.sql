CREATE TABLE ppm_campaigns (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(255)  NOT NULL,
    description TEXT,
    status      VARCHAR(32)   NOT NULL DEFAULT 'draft',
    valid_from  DATE          NOT NULL,
    valid_until DATE          NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by  UUID          NOT NULL,
    updated_by  UUID          NOT NULL,
    deleted_at  TIMESTAMPTZ,
    CONSTRAINT pk_ppm_campaigns PRIMARY KEY (id)
);
CREATE INDEX idx_ppm_campaigns_status ON ppm_campaigns (status) WHERE deleted_at IS NULL;

-- Add campaign_id to promotions (nullable M:1 FK — deferred from Phase 0, now added)
ALTER TABLE ppm_promotions ADD COLUMN campaign_id UUID;
CREATE INDEX idx_ppm_promotions_campaign_id ON ppm_promotions (campaign_id) WHERE deleted_at IS NULL;
-- Intentionally NO FK constraint: soft-deleting a campaign should not cascade or block.
-- A dangling campaign_id is acceptable (organizational grouping, not a hard reference).
