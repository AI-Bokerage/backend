-- V5__modauth_password_history.sql
--
-- SOW 1.4 "no password reuse": previous password hashes, written by
-- PasswordPolicy whenever a password is changed or reset. The starter's
-- auth_users.password_hash stays the source of truth for the current one.

CREATE TABLE IF NOT EXISTS modauth_password_history (
  id             UUID                     NOT NULL,
  user_id        UUID                     NOT NULL,
  password_hash  CHARACTER VARYING(255)   NOT NULL,
  created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT modauth_password_history_pkey PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_modauth_password_history_user
  ON modauth_password_history (user_id, created_at DESC);
