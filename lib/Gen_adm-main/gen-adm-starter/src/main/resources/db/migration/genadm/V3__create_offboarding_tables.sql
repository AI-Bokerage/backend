CREATE TABLE offboarding_jobs (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    user_id        UUID NOT NULL,
    initiated_by   UUID NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    status         VARCHAR(30) NOT NULL,
    attempt_count  INT NOT NULL DEFAULT 0,
    max_attempts   INT NOT NULL DEFAULT 5,
    next_retry_at  TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    version        BIGINT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_offboarding_jobs_tenant_user ON offboarding_jobs(tenant_id, user_id);

CREATE TABLE offboarding_steps (
    id             UUID PRIMARY KEY,
    job_id         UUID NOT NULL REFERENCES offboarding_jobs(id) ON DELETE CASCADE,
    tenant_id      UUID NOT NULL,
    user_id        UUID NOT NULL,
    sequence       INT NOT NULL,
    step_name      VARCHAR(100) NOT NULL,
    status         VARCHAR(30) NOT NULL,
    attempt_number INT NOT NULL DEFAULT 0,
    error_message  TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_offboarding_steps_job_sequence UNIQUE (job_id, sequence)
);

CREATE INDEX idx_offboarding_steps_job ON offboarding_steps(job_id);
