CREATE TABLE impersonation_sessions (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    requested_by_user_id  UUID NOT NULL,
    target_user_id        UUID NOT NULL,
    reason                VARCHAR(500) NOT NULL,
    status                VARCHAR(30) NOT NULL,
    reviewed_by_user_id   UUID,
    reviewed_at           TIMESTAMPTZ,
    ended_by_user_id      UUID,
    ended_at              TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_impersonation_sessions_tenant_status
    ON impersonation_sessions(tenant_id, status);
