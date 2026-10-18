CREATE TABLE support_tickets (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    requested_by_user_id  UUID NOT NULL,
    type                  VARCHAR(20) NOT NULL,
    subject               VARCHAR(255) NOT NULL,
    description           VARCHAR(5000) NOT NULL,
    priority              VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    started_at            TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,
    resolved_by_user_id   UUID,
    closed_at             TIMESTAMPTZ,
    closed_by_user_id     UUID,
    version               BIGINT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_support_tickets_tenant_status
    ON support_tickets(tenant_id, status);

CREATE INDEX idx_support_tickets_tenant_requested_by
    ON support_tickets(tenant_id, requested_by_user_id);
