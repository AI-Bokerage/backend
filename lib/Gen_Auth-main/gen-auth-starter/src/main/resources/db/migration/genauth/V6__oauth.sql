-- V6__oauth.sql
--
-- OAuth/OIDC login: one row per (provider, external subject) linking to an
-- auth_users row. password_hash becomes nullable — an account created purely
-- via OAuth has none until it completes the password-setup gate (see
-- OAuthLoginSuccessHandler / OAuthController.completeSignup in later tasks).

CREATE TABLE IF NOT EXISTS auth_user_oauth_identity (
    id                UUID                     PRIMARY KEY,
    user_id           UUID                     NOT NULL,
    provider          VARCHAR(50)              NOT NULL,
    provider_subject  VARCHAR(255)             NOT NULL,
    email             VARCHAR(255)             NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_auoi_provider_subject ON auth_user_oauth_identity (provider, provider_subject);
CREATE INDEX IF NOT EXISTS idx_auoi_user_id ON auth_user_oauth_identity (user_id);

ALTER TABLE auth_users ALTER COLUMN password_hash DROP NOT NULL;
