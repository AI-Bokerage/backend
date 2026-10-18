-- V2__auth_user_roles.sql
--
-- Creates the auth_user_roles read model — a local projection of role
-- assignments for internal users, keyed by (user_id, role_id).
--
-- Phase 4 JWT generation reads all role UUIDs for a user from this table
-- and emits role_ids[] in the token, enabling the multi-role permission union.
--
-- auth_users.role_id holds only one role UUID (set at creation time,
-- deprecated) — this table is the authoritative multi-role source.

CREATE TABLE IF NOT EXISTS auth_user_roles (
    user_id     UUID                     NOT NULL,
    role_id     UUID                     NOT NULL,
    tenant_id   UUID                     NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT auth_user_roles_pkey PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_aur_user_tenant
    ON auth_user_roles (user_id, tenant_id);
