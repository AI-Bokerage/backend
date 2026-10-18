# Design Amendment: Genericizing the Forked auth-svc (Java)

Supersedes the delivery mechanics (not the requirements) of `2026-07-14-login-refresh-design.md`, which was written for a from-scratch Node rewrite. Per the pivot decision, Gen_AUTH is now a genericized fork of the actual CPMS `auth-svc` Spring Boot codebase. This documents what changed given what's actually in that codebase (discovered by reading it, not assumed).

## What's already there (verified by reading the code)

Login, refresh (rotation + replay detection via family/generation), logout, change-password, session lookup, magic-link reset, super-admin login, impersonation tokens, JWKS with multi-key rotation support, Argon2id hashing at the exact cost parameters the earlier audit found, lockout (Redis), lettuce/Redis-backed refresh token store. This is a mature, well-tested codebase — the task here is targeted genericization, not building from scratch.

## Confirmed blockers (found by reading the code, not assumed)

1. **`V2__auth_user_roles.sql` will fail on first boot in a standalone deployment.** It runs an unconditional `INSERT INTO auth_user_roles SELECT ... FROM user_roles WHERE ...` — `user_roles` is a table owned by ADM-SVC that doesn't exist here. Must be fixed before this service can even start against a fresh database.
2. **`TenantSlugResolver`** runs a raw JDBC query against a `tenants` table owned by TNT-SVC. It already degrades gracefully (try/catch → empty string) so it's not a startup blocker, but it's dead weight in a standalone deployment and should be removed as part of genericizing the tenant model.
3. **No `/register` endpoint exists anywhere.** Users are created only via `AuthBootstrapService` (already deleted — it existed solely to react to another service's provisioning event) or a local dev-only seeder. This is a real gap against the approved requirements, not something to work around.
4. **Token delivery is cookies-only**, deeply wired (legacy-cookie-clearing logic in `AuthController`, `AuthCookieFactory`). The original design decided JSON-body-only; per your correction (make things configurable, don't force one shape), this becomes a config toggle instead — keep the existing cookie code as one mode.

## Decisions from this round

- **Token delivery**: new `auth.token-delivery-mode` property, `cookie` (default — preserves all existing behavior) or `json` (tokens in the response body, for mobile/cross-origin/non-browser clients).
- **`AuthReconciliationService`** (dormant, off by default, one-time ADM-SVC migration-repair tool referencing `internal_users`/`user_roles`): removed. No standalone deployment will ever need this specific repair tool.
- **Tenant model**: keep `tenant_id` NOT NULL at the schema level rather than migrating three tables to nullable — the codebase already has a working sentinel-UUID pattern for tenant-independent actors (`PLATFORM_TENANT_ID = 00000000-0000-0000-0000-000000000000`, already used for super-admin). Single-tenant consumers use that same sentinel as their one tenant_id; multi-tenant consumers use real per-org UUIDs. This reuses an existing, already-correct pattern instead of introducing a new nullable-column migration with its own risk.
- **`TenantSlugResolver`**: removed. `tenant_slug` claim becomes an empty string always (or the claim is dropped — see plan Task 4 for the exact call). No replacement cross-service lookup — a standalone service doesn't have a `tenants` table to resolve from.

## New endpoints needed (gap against the approved requirements)

| Method | Path | Gate | Notes |
|---|---|---|---|
| POST | `/api/v1/auth/register` | `auth.registration-mode=open` (default) | Creates `auth_users` row (Argon2id hash), assigns `PLATFORM_TENANT_ID` unless a `tenantId` is supplied. Never accepts a `roleId`. |
| POST | `/internal/auth/users` | `X-Internal-Secret` (existing `InternalTokenAuthFilter`) | New sibling endpoint alongside the existing `revoke-sessions` one on `InternalUserController` — the only path that can set `roleId` at creation time. |
| POST | `/api/v1/auth/logout-all` | valid refresh token in request | Self-service version of the existing internal-only `revoke-sessions` — a user revoking every one of their own sessions, not an admin acting on someone else's. |

## Explicitly out of scope for this pass

MFA (confirmed absent in the original audit, still absent here), OAuth/social login, changing the Redis-backed permission-cache design (that's a legitimate generic pattern already, not CPMS-specific), renaming `tenant_id` to something else (the word itself isn't CPMS-branded, no need to touch it).
