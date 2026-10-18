-- V1__modauth_roles_terms_invitations.sql
--
-- Adds what gen-auth-starter deliberately leaves out of auth_users: a named
-- role per user (SUPER_ADMIN / TENANT_ADMIN / TEAM_LEAD / BROKER) for
-- dashboard routing, per-user terms-of-service acceptance tracking, and the
-- invitation lifecycle. Kept in its own tables (no FK to auth_users, same as
-- role_id already does) so this module never has to touch the starter's schema.

CREATE TABLE IF NOT EXISTS modauth_user_roles (
  user_id                  UUID                     NOT NULL,
  tenant_id                UUID                     NOT NULL,
  role                     CHARACTER VARYING(20)    NOT NULL,
  team_name                CHARACTER VARYING(255),
  accepted_terms_version   INTEGER                  NOT NULL DEFAULT 0,
  created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT modauth_user_roles_pkey PRIMARY KEY (user_id),
  CONSTRAINT modauth_user_roles_role_check CHECK (
    role IN ('SUPER_ADMIN', 'TENANT_ADMIN', 'TEAM_LEAD', 'BROKER')
  )
);
CREATE INDEX IF NOT EXISTS idx_modauth_user_roles_tenant ON modauth_user_roles (tenant_id);

CREATE TABLE IF NOT EXISTS modauth_invitations (
  id               UUID                     NOT NULL,
  tenant_id        UUID                     NOT NULL,
  inviter_user_id  UUID                     NOT NULL,
  name             CHARACTER VARYING(255)   NOT NULL,
  email            CHARACTER VARYING(255)   NOT NULL,
  role             CHARACTER VARYING(20)    NOT NULL,
  team_name        CHARACTER VARYING(255),
  status           CHARACTER VARYING(20)    NOT NULL DEFAULT 'PENDING',
  token_hash       CHARACTER VARYING(64)    NOT NULL,
  expires_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  accepted_at      TIMESTAMP WITH TIME ZONE,
  CONSTRAINT modauth_invitations_pkey            PRIMARY KEY (id),
  CONSTRAINT modauth_invitations_token_hash_key  UNIQUE (token_hash),
  CONSTRAINT modauth_invitations_role_check      CHECK (role IN ('TENANT_ADMIN', 'TEAM_LEAD', 'BROKER')),
  CONSTRAINT modauth_invitations_status_check    CHECK (status IN ('PENDING', 'ACCEPTED'))
);
CREATE INDEX IF NOT EXISTS idx_modauth_invitations_tenant ON modauth_invitations (tenant_id);
CREATE INDEX IF NOT EXISTS idx_modauth_invitations_email  ON modauth_invitations (email);
