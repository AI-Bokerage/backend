-- Flyway V11: Payment methods stored per tenant after provider tokenization
CREATE TABLE IF NOT EXISTS payment_methods (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL,
    payment_provider          VARCHAR(50) NOT NULL,
    external_payment_method_id VARCHAR(255) NOT NULL,
    type                      VARCHAR(50) NOT NULL,
    brand                     VARCHAR(100),
    last_four                 VARCHAR(4),
    exp_month                 INTEGER,
    exp_year                  INTEGER,
    is_default                BOOLEAN NOT NULL DEFAULT FALSE,
    status                    VARCHAR(50) NOT NULL,
    created_at                TIMESTAMPTZ,
    updated_at                TIMESTAMPTZ,
    version                   BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_method_per_tenant UNIQUE (tenant_id, external_payment_method_id)
);

CREATE INDEX IF NOT EXISTS idx_payment_methods_tenant ON payment_methods(tenant_id);
CREATE INDEX IF NOT EXISTS idx_payment_methods_status ON payment_methods(tenant_id, status);
