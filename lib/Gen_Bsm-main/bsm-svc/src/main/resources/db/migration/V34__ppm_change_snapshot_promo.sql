-- C5: Store promo code and discount applied during a plan change.
-- Both columns are nullable: NULL means no promo was used for that change.
ALTER TABLE subscription_ppm_change_snapshots
    ADD COLUMN applied_promo_code  VARCHAR(64),
    ADD COLUMN promo_discount_minor BIGINT;
