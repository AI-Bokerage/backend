-- Flyway V13: Provider price/plan IDs on plan_versions for external subscription provisioning
ALTER TABLE plan_versions ADD COLUMN IF NOT EXISTS stripe_price_id VARCHAR(255);
ALTER TABLE plan_versions ADD COLUMN IF NOT EXISTS razorpay_plan_id VARCHAR(255);
