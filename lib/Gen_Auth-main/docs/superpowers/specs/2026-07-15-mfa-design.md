# Design: TOTP Multi-Factor Authentication

Adds TOTP-based MFA (authenticator app codes) with backup codes, opt-in per user, enforceable per tenant. Currently the "Not done yet" list's biggest gap alongside OAuth.

## Scope

- **In scope**: TOTP (RFC 6238) enrollment/confirmation/disable, backup codes, per-tenant enforcement toggle, two-step login when MFA applies.
- **Out of scope**: SMS/email OTP, WebAuthn/passkeys, admin UI for viewing/resetting a user's MFA state (a future internal endpoint, not this slice), per-tenant enforcement policy beyond a single on/off flag (e.g. no "MFA required after N days" grace period).

## Data model (new migration)

```sql
CREATE TABLE auth_user_mfa (
    user_id                 UUID PRIMARY KEY,
    totp_secret_ciphertext  BYTEA NOT NULL,
    enabled                 BOOLEAN NOT NULL DEFAULT false,
    enrolled_at             TIMESTAMPTZ,
    disabled_at             TIMESTAMPTZ
);

CREATE TABLE auth_mfa_backup_code (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    code_hash   VARCHAR(64) NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ambc_user_id ON auth_mfa_backup_code (user_id);

CREATE TABLE tenant_settings (
    tenant_id      UUID PRIMARY KEY,
    mfa_required   BOOLEAN NOT NULL DEFAULT false,
    updated_at     TIMESTAMPTZ NOT NULL
);
```

`tenant_settings` is intentionally minimal — one flag today, room to grow later. No `tenants` table exists in this service by design (tenant data lives elsewhere); this table is purely additive local config, same principle as `jwt_signing_key`'s relationship to YAML config.

## Components

- **`TotpGenerator`** (new, hand-rolled, no new dependency): RFC 6238 HMAC-SHA1 counter-based code generation, ±1 time-step (30s) tolerance window for clock drift. Matches this codebase's existing pattern of implementing crypto primitives directly (hand-rolled AES-GCM, PEM parsing) rather than adding a library for something this self-contained.
- **`Base32Codec`** (new, hand-rolled, RFC 4648): encodes the raw secret for `otpauth://` URIs/QR codes; Java has no built-in Base32 (only Base64).
- **`MfaSecretEncryptionUtil`** (new): AES-256-GCM, same scheme as `KeyEncryptionUtil` (Task 2 of the JWKS rotation slice) but keyed by a **separate** new env var `MFA_SECRET_ENCRYPTION_KEY` — a compromise of one secret must not unlock the other. Same fail-fast pattern (32-byte base64, required, no default).
- **`MfaService`** (new): `enroll(userId)` → generates+persists a pending secret, returns URI+secret; `confirmEnrollment(userId, code)` → verifies, flips `enabled=true`, generates 10 backup codes (returned once, stored hashed); `disable(userId)` → clears the row + backup codes; `verifyCode(userId, code)` → checks TOTP first, falls back to backup-code match (marking it used) if TOTP fails.
- **Challenge token**: opaque random value in Redis, `mfa:challenge:<token> → {userId, attempts}`, 5-minute TTL, max 5 verify attempts before invalidation (forces a fresh `/login` call). Reuses existing Redis infra (already backs lockout/sessions/refresh tokens).
- **`TenantSettingsService`** (new): reads/writes `tenant_settings.mfa_required`, used by the login flow and a new internal endpoint.

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/auth/mfa/enroll` | valid session | Upserts a pending secret (`enabled=false`), returns `otpauth://` URI + base32 secret |
| POST | `/api/v1/auth/mfa/enroll/confirm` | valid session | Verifies code against the pending secret → `enabled=true`, returns 10 backup codes once |
| POST | `/api/v1/auth/mfa/disable` | valid session + password or TOTP code | Clears MFA state |
| POST | `/api/v1/auth/login` | none (existing) | Unchanged request shape; response is `{accessToken,...}` as today, OR `{mfaRequired: true, challengeToken, enrollmentRequired: bool}` |
| POST | `/api/v1/auth/mfa/verify-login` | challenge token + code | Redeems the challenge (and, if `enrollmentRequired`, the code confirms a fresh enrollment in the same call), issues real tokens via the existing token-issuance path |
| POST | `/internal/auth/tenants/{tenantId}/mfa-required` | `X-Internal-Secret` | Sets `tenant_settings.mfa_required` |

## Login-time enforcement logic

1. Password verified (existing flow, unchanged) — lockout/failure recording unchanged.
2. `auth_user_mfa.enabled=true` → issue challenge token, respond `{mfaRequired: true, challengeToken}`.
3. Else, `tenant_settings.mfa_required=true` AND no `auth_user_mfa` row → issue challenge token, respond `{mfaRequired: true, challengeToken, enrollmentRequired: true}` — client must call `/mfa/enroll` (using the challenge token in place of a session, since none exists yet) then `/mfa/verify-login` with the confirmation code.
4. Else → normal token issuance, unchanged.

## Error handling

- Challenge token: max 5 verify attempts, then invalidated (401, generic message) — forces a fresh `/login`. Expired/unknown token → same generic 401 as a wrong code (no case-leaking, same philosophy as refresh-token rejection).
- Backup code: single-use (`used_at` set on redemption); already-used or unknown code → same generic failure as a bad TOTP code.
- Enroll is idempotent: re-calling `/enroll` before `/enroll/confirm` overwrites the pending secret, no separate staging table needed.

## Testing

- Unit: `TotpGenerator` against RFC 6238's published test vectors; `Base32Codec` round-trip.
- Unit: `MfaService` enroll/confirm/disable/verifyCode (mocked repos); login branching (enabled→challenge / tenant-required+unenrolled→challenge-with-enrollment / neither→normal tokens); challenge-token attempt-limiting; backup-code single-use enforcement.
- Controller tests for every new endpoint plus the internal tenant-settings endpoint.
- Manual verification against real Postgres+Redis: full enroll→confirm→login-with-challenge→disable lifecycle, tenant-required-blocks-unenrolled-user scenario, backup-code redemption, challenge-token attempt-limit exhaustion.
