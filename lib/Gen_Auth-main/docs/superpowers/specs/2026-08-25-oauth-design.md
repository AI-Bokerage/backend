# Design: OAuth Login (Spring Security OAuth2 Client)

Adds third-party login via any OIDC-compliant provider (Google, Microsoft, Okta, etc. — not GitHub, which isn't full OIDC). Last unbuilt item on the "not done yet" list alongside the now-shipped MFA slice.

**Revision note**: the first draft of this spec planned to hand-roll PKCE, discovery, JWKS fetch, and ID-token validation. `build.gradle` already carries an unused `spring-boot-starter-oauth2-client` dependency ("Future SSO Support") — this revision uses it instead. It provides PKCE, discovery, JWKS, and ID-token validation for free; the only new infrastructure this service needs is a Redis-backed request store (this service is stateless, no HTTP session) and a custom success handler (so OAuth login issues this service's own JWTs instead of a framework session).

## Scope

- **In scope**: Spring Security `oauth2Login()` wired for the authorization-code+PKCE flow, generic provider config via standard `spring.security.oauth2.client.*` properties (any OIDC issuer — no hardcoded provider list), new-account creation on first OAuth login, blocking password-setup gate for accounts created this way, auto-linking to an existing password account by email match.
- **Out of scope**: non-OIDC providers (GitHub, etc.), account unlinking/multiple-identities-per-user UI, OAuth for the super-admin path, refresh of provider tokens (this service only needs the one-time identity assertion; provider access/refresh tokens are discarded after the callback), the unused `spring-security-saml2-service-provider` dependency (unrelated, not touched by this work).

## Data model (new migration `V6__oauth.sql`)

```sql
CREATE TABLE IF NOT EXISTS auth_user_oauth_identity (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL,
    provider          VARCHAR(50) NOT NULL,
    provider_subject  VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_auoi_provider_subject ON auth_user_oauth_identity (provider, provider_subject);
CREATE INDEX IF NOT EXISTS idx_auoi_user_id ON auth_user_oauth_identity (user_id);

ALTER TABLE auth_users ALTER COLUMN password_hash DROP NOT NULL;
```

`password_hash` becomes nullable — an account created purely via OAuth has none until it completes the password-setup gate below. Every code path that reads `passwordHash` for verification (normal `/login`) is unaffected: a null hash simply can never match, so an OAuth-only account can't accidentally be logged into with a guessed password before setup completes.

## Components

- **`OAuthProperties`** (new, `@ConfigurationProperties(prefix = "app.oauth")`): one field, `enabled` (default false). Gates this service's own beans below. Provider identity (issuer, client id/secret, scopes) is **not** duplicated into a custom properties class — it's configured entirely through Spring Boot's own `spring.security.oauth2.client.registration.<id>.*` / `.provider.<id>.issuer-uri` properties, which Spring Boot's `OAuth2ClientAutoConfiguration` binds into a `ClientRegistrationRepository` bean automatically (and performs OIDC discovery against `issuer-uri` itself).
- **`RedisOAuth2AuthorizationRequestRepository`** (new, `infrastructure/security/oauth`, implements `org.springframework.security.oauth2.client.web.AuthorizationRequestRepository<OAuth2AuthorizationRequest>`): replaces Spring's default `HttpSessionOAuth2AuthorizationRequestRepository`, which this service can't use (`SecurityConfig` is `SessionCreationPolicy.STATELESS`, no `HttpSession`). Stores the in-flight `OAuth2AuthorizationRequest` (which already contains Spring's generated `state` and PKCE `code_verifier`) in Redis keyed by `state`, using Spring Security's own `OAuth2ClientJackson2Module` for serialization, 10-minute TTL, deleted on `removeAuthorizationRequest`. Same shape as `RedisMfaChallengeStore` (Redis-backed, opaque-token-keyed, TTL'd, one-shot).
- **Tenant capture**: a custom `OAuth2AuthorizationRequestResolver` (`TenantAwareOAuth2AuthorizationRequestResolver`, wraps `DefaultOAuth2AuthorizationRequestResolver`) reads an optional `tenantId` query param off the initiating request and stores it in the built `OAuth2AuthorizationRequest`'s `additionalParameters()` map, so it survives the round trip (persisted by the repository above, restored on callback).
- **`OAuthLoginSuccessHandler`** (new, implements `AuthenticationSuccessHandler`, registered on `oauth2Login().successHandler(...)`): runs after Spring Security has already exchanged the code, fetched/validated the ID token, and built an `OidcUser`. Reads `email`/`sub` off the `OidcUser`, reads `tenantId` back off the `OAuth2AuthorizationRequest` (the repository above stashes the just-removed request on a request attribute for this handler to read), then:
  1. `auth_user_oauth_identity` row exists for `(provider, sub)` → load its user → `LoginExecutionService.proceedPostAuthentication(...)`.
  2. No identity, but `auth_users.email` matches an existing account **and** the OIDC `email_verified` claim is `true` → create the identity row linking to that existing user → `proceedPostAuthentication(...)`.
  3. No identity, email matches an existing account but `email_verified` is `false`/absent → **reject** (generic error response, no tokens, no account created/linked) — see Security note below.
  4. No match at all → create a new `auth_users` row (`passwordHash` omitted/null, tenant from the captured `tenantId`) + its identity row → issue a signup challenge instead of tokens.
  Writes the response itself (cookies + JSON, or JSON tokens, matching `AuthBehaviorProperties.tokenDeliveryMode` exactly like `AuthController` does) rather than delegating to Spring's default redirect behavior.

  **Security note (added after implementation review):** the original version of this spec auto-linked purely on email match, with no check that the provider actually verified the email address. Since this feature targets *any* OIDC-compliant provider (not a hardcoded, individually-vetted list), a provider that emits an `email` claim without verifying ownership would let an attacker claim a victim's email and get silently linked into the victim's existing account — a full account-takeover path (CWE-290/OWASP A07). The `email_verified` check closes this: an unverified email match is treated as a conflict, not a link. Because `auth_users.email` is unique, creating a fresh account with that same email is also impossible, so the conflict case must be rejected outright rather than falling through to account creation.
- **`OAuthLoginFailureHandler`** (new, implements `AuthenticationFailureHandler`): any exchange/validation failure (bad state, provider error, invalid ID token) → generic 401 JSON body, no reason leaked.
- **`OAuthSignupChallengeStore`** (new, Redis-backed, mirrors `MfaChallengeStore` exactly — same entry shape, same attempt-increment/delete methods): `oauth:signup:<setupToken> → {userId, attempts}`, 10-minute TTL, max 5 attempts before invalidation.
- **`AuthUserOAuthIdentityEntity`** + **`AuthUserOAuthIdentityJpaRepository`** (new, `infrastructure/persistence/entity` / `.../repository`): `findByProviderAndProviderSubject(String, String)`, `findByUserId(UUID)`.
- **`OAuthController`** (new): a single endpoint, `POST /api/v1/auth/oauth/complete-signup` — the `/authorize` and `/callback` legs need no custom controller methods at all; Spring Security's own filters (`OAuth2AuthorizationRequestRedirectFilter`, `OAuth2LoginAuthenticationFilter`) handle them at their standard paths.
- **`LoginExecutionServiceImpl` refactor**: add `LoginResult proceedPostAuthentication(AuthUserEntity user, String ipAddress, String userAgent, long loginStart)` to `LoginExecutionService`, implemented as the existing MFA-gate-check-then-`issueTokens` tail of `executeLogin` (using the same synthetic-`LoginRequest` construction `MfaController` already does for its own no-password paths, moved into `LoginExecutionServiceImpl` as a shared private helper). `executeLogin`'s password path becomes: verify password → call `proceedPostAuthentication`. `OAuthLoginSuccessHandler` and `/complete-signup` both call it directly.
- **`SecurityConfig` change**: `ClientRegistrationRepository` is injected `@Autowired(required = false)` (Spring Boot only registers it when at least one `spring.security.oauth2.client.registration.*` entry is configured) — when present, `filterChain(...)` adds `.oauth2Login(oauth -> oauth.authorizationEndpoint(a -> a.authorizationRequestResolver(tenantAwareResolver).authorizationRequestRepository(redisAuthorizationRequestRepository)).successHandler(oauthLoginSuccessHandler).failureHandler(oauthLoginFailureHandler))`; when absent, the block is skipped entirely — same `@Autowired(required = false)`-and-null-check pattern `LoginExecutionServiceImpl` already uses for `authEventPublisher`/`mfaLoginGate`. Also adds `/api/v1/auth/oauth/complete-signup` and Spring's default OAuth2 paths (`/oauth2/authorization/**`, `/login/oauth2/code/**`) to the existing `permitAll()` list (same category as the existing MFA challenge-token-authenticated entries — the caller has no session/JWT yet by definition).

## Endpoints

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/oauth2/authorization/{registrationId}` | none | Spring Security's standard initiation endpoint (no custom controller). `registrationId` matches a key under `spring.security.oauth2.client.registration.*` (e.g. `google`). Optional `?tenantId=` query param captured by the custom resolver. |
| GET | `/login/oauth2/code/{registrationId}` | none (state is the credential) | Spring Security's standard callback endpoint (no custom controller). Framework validates state/PKCE/ID-token, then invokes `OAuthLoginSuccessHandler`, which responds either with real tokens (same cookie/JSON shape as `/login`) or `{passwordSetupRequired: true, setupToken}`. |
| POST | `/api/v1/auth/oauth/complete-signup` | setup token | Body `{setupToken, password}`. Redeems the challenge, sets `password_hash` via the existing `PasswordHasher`, deletes the challenge, calls `proceedPostAuthentication`. |

## Login-time / signup-time logic

1. Browser hits `/oauth2/authorization/{registrationId}?tenantId=...` — the custom resolver builds the standard `OAuth2AuthorizationRequest` (state, PKCE challenge, nonce) plus the captured `tenantId`, the custom repository persists it to Redis keyed by state, Spring redirects to the provider.
2. Provider redirects back to `/login/oauth2/code/{registrationId}?code=&state=`. Spring's filter loads+removes the stored request from Redis (one-shot), exchanges the code (with the PKCE verifier) for tokens, fetches+validates the ID token against the provider's JWKS (issuer/audience/expiry/nonce all checked by the framework), builds an `OidcUser`.
3. `OAuthLoginSuccessHandler` resolves to an existing user (found, or auto-linked because the email matched **and** `email_verified` was true) → `proceedPostAuthentication` (MFA gate applies exactly as it does for password login — a `TENANT_USER` with MFA enabled still gets challenged) → real tokens.
4. Email matches an existing account but `email_verified` is false/absent → reject with a generic error, no account touched.
5. No match at all → create account (`password_hash = NULL`) + identity row → issue `setupToken`, respond `passwordSetupRequired`. If the user closes the tab and logs in via OAuth again later, step 3's lookup finds the identity row (created here) but the user still has no password — this repeats the same `passwordSetupRequired` challenge rather than logging them in, so an account can never end up both real-token-bearing and password-less.
6. `/complete-signup` — redeem `setupToken` → set password → `proceedPostAuthentication` (MFA gate still applies here — a tenant with `mfa_required=true` doesn't get bypassed just because the user arrived via OAuth).

## Error handling

- Authorization request (state/PKCE): Spring Security's own state-mismatch handling (400) plus this repository's 10-minute TTL/one-shot delete — expired or replayed state fails the same way stock `oauth2Login()` failures do, routed to `OAuthLoginFailureHandler`.
- ID token / code-exchange failures (bad signature, wrong issuer/audience, expired, provider error) → caught by `OAuthLoginFailureHandler`, generic 401 JSON, no reason leaked.
- Setup token: max 5 attempts (e.g. failing the existing password-strength validation), then invalidated like the MFA challenge — forces a fresh OAuth round trip from `/oauth2/authorization/{registrationId}`.
- Provider HTTP failures during discovery/token-exchange/JWKS fetch are handled by Spring Security's own `OAuth2AuthorizationException` machinery, surfaced through `OAuthLoginFailureHandler` the same as any other failure — no custom retry (matches this codebase's current fire-and-forget-on-infra-failure posture elsewhere, e.g. email/messaging).

## Testing

- Unit: `RedisOAuth2AuthorizationRequestRepository` save/load/remove round-trip and TTL (mocked `StringRedisTemplate`, same style as `RedisMfaChallengeStoreTest`); `TenantAwareOAuth2AuthorizationRequestResolver` — with and without a `tenantId` param; `OAuthSignupChallengeStore` TTL/attempt-limit behavior (mirrors `RedisMfaChallengeStoreTest`).
- Unit: `OAuthLoginSuccessHandler` linking logic — identity-found, auto-link-by-email (verified), rejected-email-collision (unverified), create-new-blocked — with mocked repos and a mocked `OidcUser`/`Authentication`.
- Unit: `LoginExecutionServiceImpl.proceedPostAuthentication` — MFA-gate-present vs. absent, mirroring the existing `executeLogin` test cases.
- Controller test for `/complete-signup` — success, expired/invalid token, attempt-limit exhaustion.
- Manual E2E against a real Google OAuth sandbox app: fresh signup → setup-gate → complete-signup → login; existing-email auto-link; repeat OAuth login of an already-linked account; tenant with `mfa_required=true` still challenges after OAuth/signup.
