# Design: Pluggable HMAC Internal-Auth Strategy

First sub-project of the CPMS-parity effort (`gap.md`, priority item 1 — "cross-cutting, needed before client-token/impersonation-token can work correctly"). Adds a second internal-caller authentication mechanism — HMAC-signed requests — alongside the existing `X-Internal-Secret` shared-header check, without hardcoding it to CPMS service names.

**Compatibility source of truth**: `CPMS-Platform/apps/auth-svc/.../infrastructure/security/filter/InternalHmacAuthFilter.java` (the verifier) and `CPMS-Platform/apps/sup-svc/src/infra/external/auth-svc.client.ts` (a real signer, `requestImpersonationToken`). Both read directly to confirm the wire format — this is not a guessed protocol.

## Scope

- **In scope**: a generic, config-driven HMAC request-verification filter (target paths configurable, not hardcoded); wiring it into `SecurityConfig` alongside the existing shared-secret filter; a new `/v1/impersonation-token` endpoint reusing the existing `ImpersonationTokenService`, guarded by this filter, proving real interop against SUP-SVC's actual caller code.
- **Out of scope**: `/v1/client-token` (doesn't exist in Gen_Auth yet — new business logic, `CLIENT`/`PROSPECT` UserType wiring; becomes sub-project 2, which adds itself to this filter's configured target paths), service-token/signoff-token/OTP endpoints (also sub-project 2), any change to CPMS-Platform itself (this repo only; CPMS-Platform is read-only reference).
- **Existing `/internal/auth/impersonation-token`** (shared-secret guarded) is untouched — this adds a second, HMAC-guarded route to the same service, it does not replace the first. A host app not integrating with CPMS's exact convention keeps using the shared-secret path.

## The exact wire format (verified, not designed)

Confirmed by reading both the verifier and a real caller in CPMS-Platform:

- Headers: `X-CPMS-Service` (caller's own name, logged only — never used as an authorization check, in either direction), `X-CPMS-Timestamp` (unix seconds, decimal string), `X-CPMS-Signature` (hex-encoded).
- `signature = HMAC-SHA256("{timestamp}:{rawBody}", INTERNAL_SERVICE_SECRET)` — the **same** secret Gen_Auth's existing `InternalTokenAuthFilter` already reads from `internal-service-secret`. No new secret to provision.
- Clock-skew tolerance: request rejected if `|now - timestamp| > 60` seconds.
- A body-less (e.g. `GET`) request signs against the literal string `"{}"`, not an empty string — confirmed from `CptClient.java`'s `EMPTY_BODY` constant, used by every GET call in that file.
- Comparison is constant-time.

**One deliberate deviation from auth-svc's own code**: auth-svc's verifier hashes the signing string as `US_ASCII` bytes; the real TS caller signs it as UTF-8 (Node's `createHmac(...).update(string)` default). These are identical today only because current payloads (UUIDs, timestamps) are pure ASCII — auth-svc's `US_ASCII` choice is a latent bug that hasn't been exercised, not a deliberate part of the protocol. Gen_Auth's verifier uses UTF-8 throughout: it matches what callers actually sign, and it's a superset-compatible choice (identical output for the ASCII-only payloads used today, correct for any future payload that isn't).

## Components

- **`InternalHmacAuthProperties`** (new, `@ConfigurationProperties(prefix = "app.internal-hmac-auth")`): `enabled` (boolean, default `false`) and `targetPaths` (`List<String>`, default empty). The filter only signs/verifies requests whose path is in this list — this is what makes the mechanism generic rather than hardcoded to `/v1/impersonation-token` specifically; a host app enables it and lists whatever paths it wants HMAC-guarded instead of secret-guarded.
- **`InternalHmacAuthFilter`** (new, `infrastructure/security/filter`, `OncePerRequestFilter`, always registered as a plain `@Component` — mirrors `InternalTokenAuthFilter`'s existing unconditional-registration style, since it already no-ops safely when `targetPaths` is empty or `enabled=false`):
  - `shouldNotFilter`: `true` (skip) unless `enabled=true` AND the request path is in `targetPaths`.
  - Reads `internal-service-secret` (same `@Value` as the existing filter). Blank/missing secret → reject every guarded request (fail closed, matches auth-svc's own posture).
  - Reads and buffers the raw request body once (needed for both signature verification and the downstream controller's own deserialization — `CachedBodyRequestWrapper`, ported directly from auth-svc's implementation since it's a correct, self-contained utility, not CPMS-specific logic).
  - Missing `X-CPMS-Timestamp`/`X-CPMS-Signature`, non-numeric timestamp, stale timestamp (>60s skew), or signature mismatch → generic 401 JSON (`{"error":"Unauthorized"}`, same body shape `InternalTokenAuthFilter` already uses — one consistent internal-auth failure shape across both mechanisms).
- **`V1InternalTokenController`** (new, `api/controller`, `@RequestMapping("/v1")`): `POST /impersonation-token`, delegating to the existing `ImpersonationTokenService` — identical business logic and response shape to the existing `/internal/auth/impersonation-token` endpoint, just reached via a different path and auth mechanism. Registered only when **both** `app.super-admin.enabled=true` (the underlying feature's own existing gate) and `app.internal-hmac-auth.enabled=true` — via `@ConditionalOnProperty(prefix = "app", name = {"super-admin.enabled", "internal-hmac-auth.enabled"}, havingValue = "true")`. If either flag is off, this controller doesn't exist and the path 404s — matching this codebase's established pattern (`MfaController` etc.) of permitAll-by-path-unconditionally-in-SecurityConfig, reachability-gated-by-conditional-bean-registration.
- **`SecurityConfig` change**: add `POST /v1/impersonation-token` to the existing `permitAll()` list (JWT auth isn't the point — the HMAC filter is this path's real gate, same relationship `InternalTokenAuthFilter` already has with `/internal/**`), and `addFilterBefore(internalHmacAuthFilter, UsernamePasswordAuthenticationFilter.class)` alongside the two existing filter registrations. The three internal-auth-adjacent filters (`internalTokenAuthFilter`, `internalHmacAuthFilter`, `jwtFilter`) each self-select via their own `shouldNotFilter`/target-path logic on disjoint path sets, so ordering between them doesn't matter — same non-interacting-filters shape auth-svc itself uses.

## Data flow

1. SUP-SVC (unchanged) computes `HMAC-SHA256("{ts}:{body}", secret)`, sends `POST /v1/impersonation-token` with `X-CPMS-Timestamp`/`X-CPMS-Signature`.
2. `InternalHmacAuthFilter` matches the path against `targetPaths`, verifies timestamp freshness and signature, wraps the request with the cached body, passes it through.
3. `V1InternalTokenController.issueImpersonationToken` runs — same `ImpersonationTokenService.issue(...)` call, same `ImpersonationTokenRequest`/`ImpersonationTokenResponse` DTOs, the existing `/internal/auth/impersonation-token` endpoint already uses.

## Error handling

- Every rejection path (missing headers, bad timestamp, stale timestamp, bad signature, blank secret) returns the same generic 401 — no information about which check failed, matching this codebase's established no-leak posture for auth failures elsewhere (refresh-token rejection, MFA challenge failure).
- A malformed/unparseable body still gets HMAC-verified against its raw bytes before the controller ever attempts to deserialize it — a forged unsigned request never reaches DTO binding.

## Testing

- Unit: `InternalHmacAuthFilter` — valid signature passes through with the body still readable downstream; missing/stale/mismatched signature all reject with 401; a path not in `targetPaths` skips verification entirely; `targetPaths` empty or `enabled=false` skips everything (existing shared-secret-only behavior fully preserved).
- Unit: verify the exact byte-for-byte compatibility claim — compute a signature using the real algorithm CPMS's TS client uses (HMAC-SHA256 over `"{ts}:{utf8-body}"`) and confirm Gen_Auth's filter accepts it. This is the test that actually proves interop, not just "the filter works."
- Controller test for `V1InternalTokenController` — same shape as the existing `ImpersonationTokenController` test, reusing the same service mock.
- Config test (`ApplicationContextRunner` style, matching `MfaConfigTest`/`OAuthConfigTest`): `V1InternalTokenController` bean absent when either flag is off, present when both are on.
- Manual: not required against a live SUP-SVC for this sub-project (no network dependency, the byte-for-byte signature test above is the real compatibility proof) — full live verification happens at actual cutover time, out of scope here per the confirmed repo-scope decision.
