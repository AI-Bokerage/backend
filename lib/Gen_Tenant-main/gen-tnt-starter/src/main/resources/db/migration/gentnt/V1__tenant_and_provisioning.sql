-- gen-tnt-starter/src/main/resources/db/migration/gentnt/V1__tenant_and_provisioning.sql
CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    slug VARCHAR(63) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    region VARCHAR(64),
    primary_owner_user_id UUID NOT NULL,
    provisioning_job_id UUID,
    idempotency_key VARCHAR(255) UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE provisioning_job (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL,
    callback_token VARCHAR(255) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    last_error TEXT,
    context TEXT NOT NULL DEFAULT '{}',
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_provisioning_job_status ON provisioning_job (status);
CREATE INDEX idx_provisioning_job_tenant_id ON provisioning_job (tenant_id);

CREATE TABLE provisioning_step (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_order INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_provisioning_step_job_id ON provisioning_step (job_id);
