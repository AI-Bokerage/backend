# Design: Operational JWKS Multi-Key Rotation

The multi-key registry (`JwtKeyRegistry`, `JwksService`, `RsaKeyConfig`) already exists and works — README documents a restart-based rotation procedure (edit `jwt.keys`/`jwt.active-kid` in YAML, rolling-restart). This slice adds operational tooling on top: rotate and retire signing keys at runtime, no YAML edit or restart required, for `jwt.signing-mode=local` deployments.

## Scope

- **In scope**: local signing mode only. Runtime rotation persists new keys to Postgres so they survive a restart.
- **Out of scope**: `kms` signing mode (creating a new AWS KMS key/alias is an AWS-side provisioning action, not something this endpoint should do — keep the existing documented manual procedure for KMS). Automatic time-based retirement of old keys (retirement is an explicit operator call, not a scheduled job).

## Data model (Flyway V3)

```sql
CREATE TABLE jwt_signing_key (
    kid                     TEXT PRIMARY KEY,
    private_key_ciphertext  BYTEA NOT NULL,
    public_key_pem          TEXT NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE jwt_active_signing_key (
    id   SMALLINT PRIMARY KEY CHECK (id = 1),
    kid  TEXT NOT NULL REFERENCES jwt_signing_key(kid)
);
```

Purely additive: existing `jwt.keys`/legacy `jwt.key-id` YAML configuration is untouched and keeps working exactly as documented today. DB-sourced keys merge into the same registry at boot.

## Components

- **`KeyEncryptionUtil`** (new): AES-256-GCM encrypt/decrypt for the private key at rest. New required env var `JWT_KEY_ENCRYPTION_SECRET` — 32-byte value, base64-encoded. Fails fast at startup (same pattern as `INTERNAL_API_SECRET`) if missing or the wrong length.
- **`JwtKeyRegistry`** (modified): becomes a thread-safe mutable registry — `ConcurrentHashMap<String, JwtKeyEntry>` + `AtomicReference<String>` for the active kid — with new `addKey(entry)`, `setActiveKid(kid)`, `removeKey(kid)` methods. Invariants: no duplicate kid on add, active kid must exist in the map, the active kid can never be removed.
- **`RsaKeyConfig`** (modified): after building the registry from YAML as today, loads every row from `jwt_signing_key`, decrypts each private key, and adds it to the registry via the same `validateKeypair` round-trip check YAML keys go through. If `jwt_active_signing_key` has a row, that kid becomes the active signer (overrides `jwt.active-kid`).
- **`JwtKeyRotationService`** (new, `infrastructure/security/jwt/jwks/`):
  - `rotate()`: generates a 2048-bit RSA keypair (`KeyPairGenerator`, local mode only — throws if `signing-mode=kms`), encrypts the private key, inserts the `jwt_signing_key` row and upserts `jwt_active_signing_key` in one transaction, then — only after commit — pushes the new entry into the live registry and flips the active kid. Returns `{kid, publicKeyPem}`.
  - `retire(kid)`: 404 if `kid` isn't in `jwt_signing_key` (retiring a YAML-sourced key isn't this endpoint's job — those follow the existing restart procedure), 409 if `kid` is the current active key. Otherwise deletes the DB row, then removes it from the live registry.
- **`JwksAdminController`** (new): mounted under `/internal/auth/keys` — already covered by the existing `InternalTokenAuthFilter` (`X-Internal-Secret` header), no new security wiring.
  | Method | Path | Response |
  |---|---|---|
  | POST | `/internal/auth/keys/rotate` | `201 {kid, publicKeyPem}` |
  | POST | `/internal/auth/keys/{kid}/retire` | `204`, `404`, or `409` |
  | GET | `/internal/auth/keys` | `200 {activeKid, kids: [...]}` |
- **`JwksService`** (modified): `toJsonObject()` builds the `JWKSet` fresh from `registry.allEntries()` on every call instead of once in the constructor, so `/.well-known/jwks.json` reflects rotations immediately.

## Data flow / error handling

- **Rotate**: generate → encrypt → DB transaction (insert key row + upsert active-kid row) → commit → in-memory swap. If the transaction fails, nothing changes in memory — no partial state possible.
- **Retire**: validate (404/409) → DB delete → in-memory removal. Old key stays valid for verification until explicitly retired; JWKS always reflects exactly what's in the registry.
- **Boot-time**: DB-sourced keys go through the same duplicate-kid and sign/verify checks as YAML keys. If `JWT_KEY_ENCRYPTION_SECRET` is missing/malformed while rotated keys exist in the DB, boot fails loudly rather than starting with a broken registry.

## Testing

- Unit: `JwtKeyRegistry` mutation methods and their invariants.
- Unit: `KeyEncryptionUtil` round-trip and wrong-length-secret rejection.
- Unit: `JwtKeyRotationService.rotate()`/`retire()` against a mocked repository — DB write happens before the in-memory swap; `retire()` on the active kid throws before any DB call.
- Controller test: all three endpoints, confirming they fall under the existing `/internal/**` secret gate.
- Manual verification (same style as the existing README section, against real Postgres): rotate → JWKS shows 2 keys → new key signs a token that verifies → retire old kid → JWKS drops to 1 key → retiring the active kid returns 409.
