CREATE TABLE data_exports (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    requested_by_user_id  UUID NOT NULL,
    status                VARCHAR(20) NOT NULL,
    snapshot_json         TEXT,
    record_count          BIGINT NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    revoked_at            TIMESTAMPTZ,
    revoked_by_user_id    UUID,
    version               BIGINT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_data_exports_tenant_status
    ON data_exports(tenant_id, status);
