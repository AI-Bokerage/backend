-- V1__init.sql
-- AUTH-SVC schema baseline.
-- Derived from the live Hibernate-generated schema (2026-06-14).
--
-- Uses CREATE TABLE IF NOT EXISTS throughout so this script is safe in two
-- scenarios:
--   1. Existing environment (Flyway baseline-version: 0, baseline-on-migrate: true):
--      Tables already exist from prior ddl-auto:create runs. IF NOT EXISTS
--      makes every statement a no-op. Flyway marks V1 as applied and proceeds.
--   2. Fresh environment (no prior schema):
--      Tables do not exist. All CREATE TABLE statements execute normally.
--      Flyway marks V1 as applied and proceeds.
--
-- DO NOT modify this file after it has been applied to any environment.
-- Schema changes go in V2, V3, etc.

-- ─── auth_users ───────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS auth_users (
  id            UUID                     NOT NULL,
  tenant_id     UUID                     NOT NULL,
  email         CHARACTER VARYING(255)   NOT NULL,
  password_hash CHARACTER VARYING(255)   NOT NULL,
  user_type     CHARACTER VARYING(40)    NOT NULL,
  role_id       UUID,
  active        BOOLEAN                  NOT NULL,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT auth_users_pkey          PRIMARY KEY (id),
  CONSTRAINT auth_users_email_key     UNIQUE (email),
  CONSTRAINT auth_users_user_type_check CHECK (
    (user_type)::TEXT = ANY (ARRAY[
      'TENANT_USER'::CHARACTER VARYING,
      'CLIENT'::CHARACTER VARYING,
      'SUPER_ADMIN'::CHARACTER VARYING,
      'PROSPECT'::CHARACTER VARYING,
      'SUPER_ADMIN_IMPERSONATING'::CHARACTER VARYING
    ]::TEXT[])
  )
);
CREATE INDEX IF NOT EXISTS idx_auth_users_tenant_id ON auth_users (tenant_id);

-- ─── auth_sessions ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS auth_sessions (
  id                 UUID                     NOT NULL,
  user_id            UUID                     NOT NULL,
  tenant_id          UUID                     NOT NULL,
  role_id            UUID,
  user_type          CHARACTER VARYING(40)    NOT NULL,
  ip_address         CHARACTER VARYING(45),
  user_agent         CHARACTER VARYING(512),
  device_fingerprint CHARACTER VARYING(64),
  active             BOOLEAN                  NOT NULL,
  impersonation      BOOLEAN                  NOT NULL,
  created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
  last_activity_at   TIMESTAMP WITH TIME ZONE,
  expires_at         TIMESTAMP WITH TIME ZONE,
  revoked_at         TIMESTAMP WITH TIME ZONE,
  CONSTRAINT auth_sessions_pkey            PRIMARY KEY (id),
  CONSTRAINT auth_sessions_user_type_check CHECK (
    (user_type)::TEXT = ANY (ARRAY[
      'TENANT_USER'::CHARACTER VARYING,
      'CLIENT'::CHARACTER VARYING,
      'SUPER_ADMIN'::CHARACTER VARYING,
      'PROSPECT'::CHARACTER VARYING,
      'SUPER_ADMIN_IMPERSONATING'::CHARACTER VARYING
    ]::TEXT[])
  )
);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_user_id    ON auth_sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_tenant_id  ON auth_sessions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_active     ON auth_sessions (active);
CREATE INDEX IF NOT EXISTS idx_auth_sessions_expires_at ON auth_sessions (expires_at);

-- ─── auth_refresh_tokens ──────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
  id          UUID                     NOT NULL,
  user_id     UUID                     NOT NULL,
  tenant_id   UUID                     NOT NULL,
  session_id  UUID                     NOT NULL,
  token_hash  CHARACTER VARYING(64)    NOT NULL,
  family_id   UUID                     NOT NULL,
  generation  INTEGER                  NOT NULL,
  used        BOOLEAN                  NOT NULL,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  used_at     TIMESTAMP WITH TIME ZONE,
  revoked_at  TIMESTAMP WITH TIME ZONE,
  CONSTRAINT auth_refresh_tokens_pkey           PRIMARY KEY (id),
  CONSTRAINT auth_refresh_tokens_token_hash_key UNIQUE (token_hash)
);
CREATE INDEX IF NOT EXISTS idx_art_user_id    ON auth_refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_art_session_id ON auth_refresh_tokens (session_id);
CREATE INDEX IF NOT EXISTS idx_art_family_id  ON auth_refresh_tokens (family_id);
CREATE INDEX IF NOT EXISTS idx_art_expires_at ON auth_refresh_tokens (expires_at);

-- ─── auth_audit_logs ──────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS auth_audit_logs (
  id         UUID                     NOT NULL,
  tenant_id  UUID,
  user_id    UUID,
  action     CHARACTER VARYING(50)    NOT NULL,
  ip_address CHARACTER VARYING(45),
  user_agent CHARACTER VARYING(512),
  details    CHARACTER VARYING(1000),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT auth_audit_logs_pkey PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_aal_user_id    ON auth_audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_aal_action     ON auth_audit_logs (action);
CREATE INDEX IF NOT EXISTS idx_aal_created_at ON auth_audit_logs (created_at);

-- ─── auth_login_attempts ──────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS auth_login_attempts (
  id             UUID                     NOT NULL,
  tenant_id      UUID,
  user_id        UUID,
  email          CHARACTER VARYING(255)   NOT NULL,
  ip_address     CHARACTER VARYING(45),
  user_agent     CHARACTER VARYING(512),
  success        BOOLEAN                  NOT NULL,
  failure_reason CHARACTER VARYING(100),
  attempted_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT auth_login_attempts_pkey PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ala_email        ON auth_login_attempts (email);
CREATE INDEX IF NOT EXISTS idx_ala_user_id      ON auth_login_attempts (user_id);
CREATE INDEX IF NOT EXISTS idx_ala_attempted_at ON auth_login_attempts (attempted_at);

-- ─── platform_super_admin ─────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS platform_super_admin (
  id            UUID                     NOT NULL,
  email         CHARACTER VARYING(255)   NOT NULL,
  password_hash CHARACTER VARYING(255)   NOT NULL,
  active        BOOLEAN                  NOT NULL,
  created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  CONSTRAINT platform_super_admin_pkey      PRIMARY KEY (id),
  CONSTRAINT platform_super_admin_email_key UNIQUE (email)
);
