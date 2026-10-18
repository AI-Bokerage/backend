# Design: Register + Login + Refresh Rotation + Logout

## Purpose

First real logic slice for Gen_AUTH. Everything before this was infra scaffold (JWKS stub, Prisma schema, empty route). This gives Gen_AUTH a working, generic auth flow any project can call: create a user, log in, keep a session alive via rotating refresh tokens, log out one session or all of them.

Ported design (not code) from `pre-context/auth-svc`'s `RefreshTokenServiceImpl` — see `PMP_Service_Genericization_Categories.md` in `PMP CANADA/` for why this piece specifically was flagged Category A (generic, worth the full Node rewrite).

## Scope

**In scope:** register, login, refresh (rotation + replay detection), logout (single session), logout-all (every session for a user), an internal admin-provisioning endpoint, registration on/off toggle.

**Out of scope for this slice** (each is its own future slice): lockout enforcement on login, JWKS multi-key rotation (single static key stays), magic-link password reset, OAuth/social login.

## Endpoints

All under `/api/v1/auth` unless noted. No JWT-auth middleware guards these — the refresh token itself is the credential where one's needed.

| Method | Path                                                       | Body                                  | Response                                       | Notes                                                                                                                                                                                                            |
| ------ | ---------------------------------------------------------- | ------------------------------------- | ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| POST   | `/register`                                                | `{ email, password, orgId? }`         | `201 { userId }`                               | Only active when `REGISTRATION_MODE=open`. `orgId` is caller-supplied (opaque, per the schema's existing design) and optional. **Never accepts `roles`** — public self-signup can't grant itself elevated roles. |
| POST   | `/login`                                                   | `{ email, password }`                 | `200 { accessToken, refreshToken, expiresIn }` |                                                                                                                                                                                                                  |
| POST   | `/refresh`                                                 | `{ refreshToken }`                    | `200 { accessToken, refreshToken, expiresIn }` | Old token dead the instant this succeeds.                                                                                                                                                                        |
| POST   | `/logout`                                                  | `{ refreshToken }`                    | `204`                                          | Revokes only that token's family (this session/device).                                                                                                                                                          |
| POST   | `/logout-all`                                              | `{ refreshToken }`                    | `204`                                          | Looks up `userId` from the presented token, revokes every family belonging to that user.                                                                                                                         |
| POST   | `/internal/v1/users` (top-level, not under `/api/v1/auth`) | `{ email, password, orgId?, roles? }` | `201 { userId }`                               | Always available regardless of `REGISTRATION_MODE`. Gated by `X-Internal-Secret` header matching `INTERNAL_API_SECRET` env var. The only path that can set `roles` at creation time.                             |

## Registration configurability

- `REGISTRATION_MODE` env var: `open` (default) or `disabled`.
- `disabled` → `POST /register` returns `403 { error: "self-registration disabled" }`. Route still exists (not hidden behind 404) since this is a deployment config choice, not a secret.
- `INTERNAL_API_SECRET` env var (required, no default) → gates `/internal/v1/users`, independent of `REGISTRATION_MODE`. A consumer's own admin backend calls this to provision users over HTTP without touching the DB directly.

## Token mechanics

- **Refresh token**: 32 random bytes (`crypto.randomBytes`), base64url-encoded, returned to the client raw exactly once. Only its SHA-256 hash is ever persisted (`RefreshToken.tokenHash`, unique).
- **Rotation**: on a valid `/refresh` call, the presented token's row gets `revokedAt = now()`; a new row is inserted with `generation = old.generation + 1`, same `familyId`, same `absoluteExpiresAt` (never extended past the original login's ceiling).
- **Replay detection (Postgres-only, per approved decision)**: if the presented token's row already has `revokedAt` set, it's reuse of an already-rotated-away token → revoke every row sharing that `familyId` (`updateMany` on `familyId`, idempotent) → respond generic `401 { error: "invalid refresh token" }`. Log server-side as a possible-replay event (`console.warn`) — don't put that detail in the response.
- **Absolute expiry**: if `now > absoluteExpiresAt` regardless of `revokedAt`, treat as expired — reject with the same generic 401, no need to force re-login via a different error shape.
- **Access token JWT claims**: `{ sub: userId, orgId, roles, jti }`. `jti` is a fresh UUID per access token issuance (not tied to the refresh family). No `client_ids` claim — that was PMP-specific, not part of a generic service.
- **Password hashing**: Argon2id via the `argon2` package, using the same cost parameters found in `pre-context/auth-svc`'s `SecurityConfig.java` (salt=16B, hash=32B, parallelism=1, memory=64MB, iterations=3) for continuity with a known-good baseline rather than picking new numbers.

## Data model

No schema changes needed — `User` and `RefreshToken` (already in `prisma/schema.prisma`) cover everything above.

## Error handling

- `400` — zod validation failure (malformed email, short password, etc.)
- `401` — invalid login credentials; invalid, expired, or replayed refresh token
- `403` — internal endpoint called without a valid `X-Internal-Secret`; register called while `REGISTRATION_MODE=disabled`
- `409` — register/internal-create with an email that already exists

## Testing strategy

- **Unit tests** (no DB): the rotation/replay decision logic as a pure function taking a token-row lookup result and returning `{ action: 'rotate' | 'reject-expired' | 'reject-replay' }` — mirrors the `applyFirmScope` pattern already used in PMP CANADA's `scope.test.ts`. Fast, runs in CI without Postgres.
- **Manual/integration check** (documented in README, run against real Postgres+Redis via `docker-compose up`): register → login → refresh twice → confirm old refresh token now 401s → logout → confirm that family's tokens 401 → logout-all with a second session active → confirm both sessions 401.

## Security notes

- Public `/register` never accepts a `roles` field — only `/internal/v1/users` can set roles, and that's gated by a shared secret.
- Replay responses are generic (no "this looks like reuse" detail leaked to the caller).
- `INTERNAL_API_SECRET` has no default value — service should fail to start (or fail-closed on that route) if it's unset in a non-development environment. (Exact fail-closed-vs-fail-to-start behavior is an implementation detail for the plan, not re-litigated here.)
