-- V5__mfa.sql
--
-- TOTP multi-factor authentication: per-user enrollment (auth_user_mfa),
-- single-use backup codes (auth_mfa_backup_code), and per-tenant enforcement
-- (tenant_settings). Runs unconditionally through the starter's own Flyway
-- instance regardless of app.mfa.enabled — empty unused tables when off cost
-- nothing, matching platform_super_admin's precedent.
--
-- tenant_settings is intentionally minimal (one flag today, room to grow) —
-- this service has no `tenants` table by design (tenant data lives elsewhere);
-- this table is purely additive local config, same principle as
-- jwt_signing_key's relationship to YAML config.

CREATE TABLE IF NOT EXISTS auth_user_mfa (
    user_id                 UUID                     PRIMARY KEY,
    totp_secret_ciphertext  BYTEA                    NOT NULL,
    enabled                 BOOLEAN                  NOT NULL DEFAULT false,
    enrolled_at             TIMESTAMP WITH TIME ZONE,
    disabled_at             TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS auth_mfa_backup_code (
    id          UUID                     PRIMARY KEY,
    user_id     UUID                     NOT NULL,
    code_hash   VARCHAR(64)              NOT NULL,
    used_at     TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ambc_user_id ON auth_mfa_backup_code (user_id);

CREATE TABLE IF NOT EXISTS tenant_settings (
    tenant_id      UUID                     PRIMARY KEY,
    mfa_required   BOOLEAN                  NOT NULL DEFAULT false,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
