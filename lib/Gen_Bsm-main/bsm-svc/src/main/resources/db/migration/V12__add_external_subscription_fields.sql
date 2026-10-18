-- Flyway V12: Add provider sync fields to subscriptions
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(50);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS external_subscription_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_subscriptions_external_id ON subscriptions(external_subscription_id) WHERE external_subscription_id IS NOT NULL;
