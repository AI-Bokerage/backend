# Design: Client-Token, Service-Token, OTP, Users-Exists Endpoints

Second sub-project of the CPMS-parity effort (`gap.md` §1, priority item 2 — the self-contained
slice of the missing-endpoints gap). Adds four independent internal endpoints that all reuse
existing gen-auth-starter infrastructure (JWT issuance, `TenantSlugResolver`, `InternalTokenAuthFilter`,
`InternalHmacAuthFilter`, `EmailService`, Redis-backed stores) — none require a new cross-service
HTTP client. `POST /api/v1/auth/signoff-token` (the fifth missing endpoint, which needs
ADM-SVC/CPT-SVC-equivalent lookups and its own authorization decision) is deliberately out of
scope — it is sub-project 3.

**Compatibility source of truth**: `CPMS-Platform/apps/auth-svc` — `ClientTokenController`/
`ClientTokenServiceImpl`, `ServiceTokenController`/`ServiceTokenService`, `InternalOtpController`/
`OtpServiceImpl`/`OtpStore`, `InternalUserController`'s `/exists` handler. Read directly during
brainstorming; DTO shapes and flows below are transcribed from that code, not guessed.

## Scope

- **In scope**: `POST /v1/client-token`, `POST /internal/service-token`, `POST /internal/otp/request`,
  `POST /internal/otp/verify`, `GET /internal/auth/users/exists`.
- **Out of scope**: `POST /api/v1/auth/signoff-token` (sub-project 3 — needs an ADM/CPT-equivalent
  client abstraction and its own authorization design). Any change to CPMS-Platform itself
  (this repo only). Outbox/audit-tier routing (gap §3/§6 — separate sub-project).

## Components

### E) `GET /internal/auth/users/exists`

- **`AuthUserJpaRepository`** (modify): add `boolean existsByEmail(String email)` — a derived
  query, global (not `AndActiveTrue`-scoped), matching CPMS's exact semantics: an inactive
  account still counts as "exists" for invite-flow dedup purposes.
- **`InternalUserController`** (modify): add `@GetMapping("/exists")` returning
  `ResponseEntity<Boolean>` from `@RequestParam String email`. No new gating — this controller
  is already unconditionally registered and already sits behind `InternalTokenAuthFilter` via
  the `/internal/**` path prefix, same as its sibling `createUser`/`revokeSessions` handlers.

### B) `POST /internal/service-token`

- **`TenantConstants`** (modify): add `SERVICE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099")`
  — a stable sentinel `sub` for service-to-service tokens, alongside the existing
  `PLATFORM_TENANT_ID`. (CPMS's own `...0001` is already used there for a different sentinel in
  auth-svc's codebase; `...0099` avoids any collision with values gen-auth-starter itself may
  already reserve, while staying obviously a sentinel.)
- **`ServiceTokenService`** (new interface) / **`ServiceTokenServiceImpl`** (new,
  `application/impl/`): `issue(String callerService)`. Builds `JwtClaims(SERVICE_ACCOUNT_ID,
  PLATFORM_TENANT_ID, "platform", List.of(), UserType.SUPER_ADMIN, now, now+5min, "svc:"+callerService,
  null)`, signs via `JwtUtils.generateAccessToken`, returns `ServiceTokenResponse`. No DB write — a
  pure stateless mint, matching CPMS. Gated
  `@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")`
  (mirrors `ImpersonationTokenServiceImpl` — this mints a privileged `SUPER_ADMIN`-scoped token, so
  it rides the same feature flag). TTL configurable via
  `@Value("${jwt.service-token.expiration-minutes:5}")`.
- **`ServiceTokenResponse`** (new record, `api/dto/response/`): `String accessToken, int expiresIn`.
  (No `sessionId` — unlike impersonation/client tokens, nothing is persisted to correlate.)
- **`ServiceTokenController`** (new, `api/controller/`): `@RequestMapping("/internal")`,
  `@PostMapping("/service-token")`, reads `@RequestHeader(value = "X-CPMS-Service", defaultValue = "unknown")`,
  same `@ConditionalOnProperty` as the service. Guarded by the pre-existing `InternalTokenAuthFilter`
  via the `/internal/**` prefix — no new filter wiring needed.

### A) `POST /v1/client-token`

- **`ClientTokenRequest`** (new record, `api/dto/request/`): `@NotNull UUID clientUserId,
  @NotNull UUID tenantId, @Nullable String tenantSlug, @NotNull UUID roleId, @NotBlank String sessionId`.
- **`ClientTokenResponse`** (new record, `api/dto/response/`): `String accessToken, int expiresIn,
  String sessionId`.
- **`ClientTokenService`** (new interface) / **`ClientTokenServiceImpl`** (new,
  `application/impl/`): mirrors `ImpersonationTokenServiceImpl` almost exactly — resolve
  `tenantSlug` via request value or `TenantSlugResolver.resolve(tenantId)`, persist an
  `AuthSessionEntity` (`userId=clientUserId, tenantId, roleId, userType=CLIENT,
  impersonation=false, active=true, expiresAt=now+TTL, lastActivityAt=now`), build
  `JwtClaims(clientUserId, tenantId, tenantSlug, List.of(roleId), UserType.CLIENT, now, expiresAt,
  sessionId, null)`, sign, return. TTL via `@Value("${jwt.client-token.expiration-minutes:15}")`.
  Gated `@ConditionalOnProperty(prefix = "app.client-token", name = "enabled", havingValue = "true")`
  — its own flag, since (unlike service-token) this isn't conceptually tied to super-admin.
- **`V1InternalTokenController`** (modify): add a second handler,
  `@PostMapping("/client-token")`, calling `ClientTokenService.issue(request)`. Extend the class's
  `@ConditionalOnProperty` — currently `{"super-admin.enabled", "internal-hmac-auth.enabled"}`
  gates the whole controller including the existing impersonation-token handler. Since
  client-token must NOT require `app.super-admin.enabled`, the controller-level
  `@ConditionalOnProperty` is removed; each handler method's owning service bean (already
  `@ConditionalOnProperty`-gated: `ImpersonationTokenService` needs `super-admin.enabled`,
  `ClientTokenService` needs `client-token.enabled`) is injected as `@Autowired(required = false)`,
  and each handler method 404s (via a null-check returning `ResponseEntity.notFound()`) when its
  backing service bean is absent. `app.internal-hmac-auth.enabled` remains required for the whole
  controller (both handlers rely on the HMAC filter for auth, add a class-level
  `@ConditionalOnProperty(prefix = "app.internal-hmac-auth", name = "enabled", havingValue = "true")`).
  The existing `assertGuardedByHmacFilter()` `@PostConstruct` gains a second check: when
  `ClientTokenService` is present, `target-paths` must also contain `/v1/client-token` (same
  fail-fast pattern, same reasoning — an enabled-but-unguarded token-mint endpoint is a Critical).
- **`SecurityConfig`** (modify): add `/v1/client-token` to the existing `permitAll()` list
  alongside `/v1/impersonation-token` (HMAC filter is the real gate, same relationship).

### C) OTP request/verify

- **`OtpRequestDto`** (new record, `api/dto/request/`): `@Email @NotBlank String toEmail,
  @NotBlank String purpose`.
- **`OtpVerifyDto`** (new record, `api/dto/request/`): `@NotNull UUID otpId, @NotBlank String code`.
- **`OtpIssuedResponse`** (new record, `api/dto/response/`): `UUID otpId`.
- **`OtpVerifiedResponse`** (new record, `api/dto/response/`): `boolean verified`.
- **`OtpStore`** (new interface, `domain/port/`): `UUID issue(String codeHash, Duration ttl)`;
  `boolean verifyAndConsume(UUID otpId, String codeHash)` — atomic check-and-delete regardless of
  match outcome (always single-use, no replay even on a wrong-code attempt, matching CPMS).
- **`RedisOtpStore`** (new, `infrastructure/cache/`): mirrors `RedisOAuthSignupChallengeStore`'s
  shape — key schema `otp:<otpId>` → the SHA-256 hex hash of the code as the stored value (not a
  JSON entry, since there's nothing else to store), TTL from the `issue` call. `verifyAndConsume`
  does a Redis `GET` + compare + `DEL` (accept the small non-atomicity vs. a Lua script — this
  matches the existing store's own consistency level, and a double-fire race only wastes one
  extra failed attempt, not a security hole, since the value is deleted either way after the
  first read completes).
- **`OtpService`** (new interface) / **`OtpServiceImpl`** (new, `application/impl/`):
  `requestOtp(String toEmail, String purpose)` → generate 6-digit numeric code via `SecureRandom`
  (zero-padded, `String.format("%06d", random.nextInt(1_000_000))`), SHA-256 hash it, call
  `otpStore.issue(hash, Duration.ofMinutes(otpTtlMinutes))`, call
  `emailService.sendOtpCode(toEmail, code, purpose)` with the **plaintext** code, return the
  `otpId`. `verifyOtp(UUID otpId, String code)` → hash the supplied code, call
  `otpStore.verifyAndConsume(otpId, hash)`, return the boolean. TTL via
  `@Value("${app.otp.expiration-minutes:5}")`. Gated
  `@ConditionalOnProperty(prefix = "app.otp", name = "enabled", havingValue = "true")`.
- **`OtpController`** (new, `api/controller/`): `@RequestMapping("/internal/otp")`,
  `@PostMapping("/request")` and `@PostMapping("/verify")`, same `@ConditionalOnProperty` as the
  service. Guarded by `InternalTokenAuthFilter` via `/internal/**` — no new filter wiring.
- **`EmailService`** (modify interface, `application/service/`): add
  `void sendOtpCode(String toEmail, String code, String purpose)`.
- **`EmailProvider`** (modify interface, `infrastructure/email/provider/`): add
  `void sendOtpCode(String toEmail, String code, String purpose)`.
- **`SmtpEmailProvider`** / **`SesEmailProvider`** (modify): implement `sendOtpCode`, following
  the exact existing pattern in both classes (a `*_SUBJECT` constant, an `EmailHtmlTemplate.render(...)`
  HTML body naming the `purpose` and the code, a plain-text fallback body, try/catch-log-and-swallow
  around the provider call — the caller already returned before delivery completes).
- **`ProviderBackedEmailService`** (modify): implement `sendOtpCode`, `@Async("authAsync")`,
  delegating straight to `emailProvider.sendOtpCode(...)`, matching `sendPasswordResetLink`'s shape.

## Data flow

- **service-token**: caller sends `X-Internal-Secret` + `X-CPMS-Service` header, no body →
  `InternalTokenAuthFilter` verifies secret → `ServiceTokenController` → `ServiceTokenServiceImpl`
  mints and returns a 5-min `SUPER_ADMIN` JWT. No persistence.
- **client-token**: caller HMAC-signs the request → `InternalHmacAuthFilter` verifies signature →
  `V1InternalTokenController.issueClientToken` → `ClientTokenServiceImpl` resolves tenant slug,
  persists an `AuthSessionEntity`, mints and returns a 15-min `CLIENT` JWT.
- **otp request**: caller sends `X-Internal-Secret` + `{toEmail, purpose}` →
  `InternalTokenAuthFilter` verifies secret → `OtpController` → `OtpServiceImpl` generates a code,
  stores its hash in Redis with TTL, emails the plaintext code, returns `otpId`.
- **otp verify**: caller sends `X-Internal-Secret` + `{otpId, code}` → `InternalTokenAuthFilter`
  verifies secret → `OtpController` → `OtpServiceImpl` hashes the supplied code, atomically
  checks-and-consumes against Redis, returns `verified`.
- **users/exists**: caller sends `X-Internal-Secret` + `?email=` → `InternalTokenAuthFilter`
  verifies secret → `InternalUserController` → `AuthUserJpaRepository.existsByEmail`.

## Error handling

- All four `/internal/**` and `/v1/**` endpoints inherit their auth-rejection shape from the
  existing filters (`InternalTokenAuthFilter`/`InternalHmacAuthFilter`) — generic 401 JSON,
  unchanged, no new error paths to design there.
- `V1InternalTokenController`'s two handlers each 404 (not 401, not 500) when their backing
  service bean is absent — a disabled feature looks identical to a nonexistent route, matching
  this codebase's established gate-by-bean-registration convention.
- OTP verify returns `200 {"verified": false}` for a wrong code, expired/missing `otpId`, or an
  already-consumed `otpId` — no distinction leaked between these cases (matches CPMS: `OtpStore`'s
  contract makes "not found" and "wrong code" indistinguishable to the caller by design, since
  both single-use and expiry collapse to "the stored hash is gone or doesn't match").
- Service-token and client-token both fail Bean Validation (`400`) on malformed/missing required
  fields — standard Spring `@Valid` handling already used elsewhere in this codebase, no new
  exception-handler code needed.

## Testing

- Unit: `ServiceTokenServiceImpl` — issues a JWT with the exact fixed claims (`SERVICE_ACCOUNT_ID`,
  `PLATFORM_TENANT_ID`, `SUPER_ADMIN`, 5-min TTL); no DB interaction to verify (assert
  `AuthSessionJpaRepository` is never touched, if it's even injected — it isn't, per the design
  above).
- Unit: `ClientTokenServiceImpl` — session persisted with the right fields, JWT claims match,
  tenant-slug resolution falls back to `TenantSlugResolver` when request slug is blank (same test
  shape as the existing `ImpersonationTokenServiceImpl` test).
- Unit: `OtpServiceImpl` — `requestOtp` stores a hash (never the plaintext code) and calls
  `emailService.sendOtpCode` with the plaintext code; `verifyOtp` returns true only when the store
  reports a match, false for wrong code/missing id, and only ever calls `verifyAndConsume` once per
  `verifyOtp` invocation (proving single-use is enforced at the store boundary, not re-implemented
  in the service).
- Unit: `RedisOtpStore` — issue-then-verify round trip with a real/embedded Redis (or the existing
  test double pattern this codebase uses for `RedisOAuthSignupChallengeStore`), TTL expiry causes
  `verifyAndConsume` to report false, a second `verifyAndConsume` call after a successful one also
  reports false (proving consumption).
- Controller test: `InternalUserController.exists` — two cases (`existsByEmail` true/false),
  matching the exact shape of CPMS's own `InternalUserControllerTest` (already read: two Mockito
  unit tests, `@InjectMocks`, no MockMvc).
- Config test (`ApplicationContextRunner`, matching `OAuthConfigTest`/`MfaConfigTest` style): for
  each of `ServiceTokenController`/`ClientTokenService`/`OtpController` — bean absent when its flag
  is off, present when on. For `V1InternalTokenController` specifically: present with only
  `internal-hmac-auth.enabled=true` (client-token handler live, impersonation handler 404s);
  present with both `super-admin.enabled=true` and `internal-hmac-auth.enabled=true` (both live);
  absent entirely when `internal-hmac-auth.enabled=false`.
- Fail-fast test: `V1InternalTokenController` context fails to start when `client-token.enabled=true`
  + `internal-hmac-auth.enabled=true` but `target-paths` omits `/v1/client-token` (same pattern as
  the existing impersonation-token fail-fast test).
- Byte-for-byte compatibility is NOT re-tested here — client-token rides the already-verified HMAC
  filter from sub-project 1; no new wire-format claims are introduced by this sub-project.
