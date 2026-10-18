-- No RLS on this table — accept() looks up by token only, before tenant is
-- known. A forced tenant_id policy would block that lookup unconditionally.
-- Tenant isolation for create/list/cancel comes from explicit tenant_id
-- predicates in those queries instead. See docs/superpowers/specs/
-- 2026-07-24-gen-adm-invitations-design.md for the full rationale.

CREATE TABLE invitations (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    token_hash            VARCHAR(64) NOT NULL UNIQUE,
    status                VARCHAR(20) NOT NULL,
    invited_by_user_id    UUID NOT NULL,
    role_ids              TEXT NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    accepted_at           TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    cancelled_by_user_id  UUID,
    version               BIGINT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_invitations_tenant_email_status
    ON invitations(tenant_id, email, status);
