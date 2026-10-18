-- Flyway V10: Tenant billing profile — links a tenant to a payment provider customer
CREATE TABLE IF NOT EXISTS tenant_billing_profiles (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID UNIQUE NOT NULL,
    payment_provider     VARCHAR(50) NOT NULL,
    external_customer_id VARCHAR(255),
    created_at           TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ,
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_tenant_billing_profiles_tenant ON tenant_billing_profiles(tenant_id);
