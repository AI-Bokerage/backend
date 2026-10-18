-- V3__jwt_signing_keys.sql
--
-- Runtime-rotated JWT signing keys (jwt.signing-mode=local only), purely
-- additive to the existing jwt.keys YAML configuration — those keys are
-- never written here. jwt_active_signing_key is a one-row singleton
-- (id=1) tracking which DB-sourced kid, if any, is the active signer.

CREATE TABLE IF NOT EXISTS jwt_signing_key (
    kid                    TEXT                     PRIMARY KEY,
    private_key_ciphertext BYTEA                    NOT NULL,
    public_key_pem         TEXT                     NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS jwt_active_signing_key (
    id  SMALLINT PRIMARY KEY CHECK (id = 1),
    kid TEXT NOT NULL REFERENCES jwt_signing_key(kid)
);
