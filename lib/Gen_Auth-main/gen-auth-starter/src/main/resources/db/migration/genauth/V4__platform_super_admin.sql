-- V4__platform_super_admin.sql
--
-- Stores exactly one platform-level super admin account, intentionally
-- separate from auth_users so platform credentials are never co-mingled
-- with tenant user data. Runs unconditionally through the starter's own
-- Flyway instance regardless of app.super-admin.enabled — an unused empty
-- table costs nothing, matching how jwt_signing_key exists even in kms mode.

CREATE TABLE IF NOT EXISTS platform_super_admin (
    id            UUID                     PRIMARY KEY,
    email         VARCHAR(255)             NOT NULL UNIQUE,
    password_hash VARCHAR(255)             NOT NULL,
    active        BOOLEAN                  NOT NULL DEFAULT true,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
