# Genericize Forked auth-svc Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the forked `auth-svc` codebase actually runnable standalone (fix a migration that fails on first boot) and close the gap against the approved requirements: a working register flow with an on/off toggle, an internal admin-provisioning path, self-service logout-everywhere, and configurable token delivery (cookie vs JSON body) instead of forcing one.

**Architecture:** Existing layering stays: `api/controller` (HTTP, validation) → `application/service` interface + `application/impl` (business logic) → `infrastructure/persistence` (JPA). New work follows the same pattern — no new architectural layer introduced.

**Tech Stack:** Spring Boot 4.0.6, Java 21, Spring Data JPA, Spring Security (`PasswordEncoder` = Argon2id), Flyway, JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`, constructor injection in tests — see `ChangePasswordServiceImplTest.java` for the exact convention followed here).

## Global Constraints

- Public `POST /api/v1/auth/register` must never accept a `roleId` — the DTO for that endpoint has no such field at all (not "ignored", not present).
- Only `POST /internal/auth/users` (existing `X-Internal-Secret` gate via `InternalTokenAuthFilter`, already wired for the sibling `revoke-sessions` endpoint) can set `roleId` at creation time.
- Single-tenant/no-tenant callers use the existing sentinel `PLATFORM_TENANT_ID = 00000000-0000-0000-0000-000000000000` UUID (already used for super-admin) rather than a new nullable-column migration.
- Token delivery defaults to the existing cookie behavior (`auth.token-delivery-mode=cookie`) — JSON-body mode is additive, opt-in via config, never the default.
- `V2__auth_user_roles.sql` must not reference `user_roles` or any other table this service doesn't create in `V1__init.sql` — that cross-service backfill is what breaks first boot today.
- Argon2id parameters and the existing `PasswordHasher`/`PasswordEncoder` wiring are not touched by this plan — already correct per the earlier audit.
- Follow the existing test convention exactly: JUnit 5, `@ExtendWith(MockitoExtension.class)`, `@Mock` fields, service constructed via `new XyzServiceImpl(mock1, mock2, ...)` in `@BeforeEach`, no Spring context in unit tests.

---

### Task 1: Fix the blocking migration and remove the dormant reconciliation tool

**Files:**
- Modify: `src/main/resources/db/migration/V2__auth_user_roles.sql`
- Delete: `src/main/java/com/example/authsvc/infrastructure/runner/AuthReconciliationService.java`

**Interfaces:**
- Consumes: nothing.
- Produces: nothing new — this only removes code that would otherwise break every other task's ability to run against a real database.

- [ ] **Step 1: Remove the cross-service backfill from V2**

```sql
-- src/main/resources/db/migration/V2__auth_user_roles.sql — full file after this change
-- V2__auth_user_roles.sql
--
-- Creates the auth_user_roles read model — a local projection of role
-- assignments for internal users, keyed by (user_id, role_id).
--
-- Phase 4 JWT generation reads all role UUIDs for a user from this table
-- and emits role_ids[] in the token, enabling the multi-role permission union.
--
-- auth_users.role_id holds only one role UUID (set at creation time,
-- deprecated) — this table is the authoritative multi-role source.

CREATE TABLE IF NOT EXISTS auth_user_roles (
    user_id     UUID                     NOT NULL,
    role_id     UUID                     NOT NULL,
    tenant_id   UUID                     NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT auth_user_roles_pkey PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_aur_user_tenant
    ON auth_user_roles (user_id, tenant_id);
```

- [ ] **Step 2: Delete the dormant reconciliation runner**

```bash
rm src/main/java/com/example/authsvc/infrastructure/runner/AuthReconciliationService.java
```

- [ ] **Step 3: Verify it still compiles and tests pass**

Run: `./gradlew compileJava compileTestJava test --console=plain`
Expected: `BUILD SUCCESSFUL` (this class had zero other references in test code — confirmed by grep before this plan was written; if this run finds any, stop and report NEEDS_CONTEXT rather than guessing a fix)

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V2__auth_user_roles.sql src/main/java/com/example/authsvc/infrastructure/runner/AuthReconciliationService.java
git commit -m "fix migration that fails on standalone first boot, remove dormant ADM-specific reconciliation tool"
```

---

### Task 2: Genericize the tenant-slug resolver

**Files:**
- Create: `src/main/java/com/example/authsvc/domain/TenantConstants.java`
- Modify: `src/main/java/com/example/authsvc/infrastructure/security/jwt/TenantSlugResolver.java`
- Test: `src/test/java/com/example/authsvc/infrastructure/security/jwt/TenantSlugResolverTest.java`

**Interfaces:**
- Produces: `TenantConstants.PLATFORM_TENANT_ID` (`UUID`, value `00000000-0000-0000-0000-000000000000`) — consumed by Task 4 (register) and Task 5 (internal create). `TenantSlugResolver.resolve(UUID tenantId): String` keeps its exact existing signature — `LoginExecutionServiceImpl`, `RefreshTokenServiceImpl`, and `ImpersonationTokenServiceImpl` call this today and need zero changes.

- [ ] **Step 1: Extract the sentinel constant**

```java
// src/main/java/com/example/authsvc/domain/TenantConstants.java
package com.example.authsvc.domain;

import java.util.UUID;

public final class TenantConstants {

    private TenantConstants() {}

    /** Sentinel tenant id for actors that aren't scoped to a real tenant (super-admins, single-tenant deployments). */
    public static final UUID PLATFORM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
}
```

- [ ] **Step 2: Write the failing test for the genericized resolver**

```java
// src/test/java/com/example/authsvc/infrastructure/security/jwt/TenantSlugResolverTest.java
package com.example.authsvc.infrastructure.security.jwt;

import com.example.authsvc.domain.TenantConstants;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TenantSlugResolverTest {

    private final TenantSlugResolver resolver = new TenantSlugResolver();

    @Test
    void resolvesPlatformSentinelToPlatformSlug() {
        assertEquals("platform", resolver.resolve(TenantConstants.PLATFORM_TENANT_ID));
    }

    @Test
    void resolvesAnyOtherTenantIdToEmptyString() {
        assertEquals("", resolver.resolve(UUID.randomUUID()));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.authsvc.infrastructure.security.jwt.TenantSlugResolverTest" --console=plain`
Expected: FAIL to compile — `TenantSlugResolver` still requires a `JdbcTemplate` constructor argument, so `new TenantSlugResolver()` doesn't match any constructor yet.

- [ ] **Step 4: Rewrite TenantSlugResolver with no DB dependency**

```java
// src/main/java/com/example/authsvc/infrastructure/security/jwt/TenantSlugResolver.java — full file after this change
package com.example.authsvc.infrastructure.security.jwt;

import com.example.authsvc.domain.TenantConstants;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves the slug embedded in a JWT for a given tenant.
 *
 * <p>This service has no tenant directory of its own — a standalone auth
 * service doesn't own a "tenants" table. The only slug it can resolve without
 * an external dependency is the platform sentinel; every other tenant id
 * resolves to an empty string. A consumer that wants real per-tenant slugs
 * should extend this (e.g. inject its own tenant lookup) rather than this
 * class reaching into another service's database, which is what the original
 * CPMS-Platform version of this class did.
 */
@Component
public class TenantSlugResolver {

    public String resolve(UUID tenantId) {
        return TenantConstants.PLATFORM_TENANT_ID.equals(tenantId) ? "platform" : "";
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.authsvc.infrastructure.security.jwt.TenantSlugResolverTest" --console=plain`
Expected: PASS (2 tests)

- [ ] **Step 6: Run the full suite and typecheck the 3 unchanged callers still compile**

Run: `./gradlew compileJava compileTestJava test --console=plain`
Expected: `BUILD SUCCESSFUL` — `LoginExecutionServiceImpl`, `RefreshTokenServiceImpl`, `ImpersonationTokenServiceImpl` all still inject `TenantSlugResolver` and call `.resolve(...)` exactly as before; only the internals changed.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/example/authsvc/domain/TenantConstants.java src/main/java/com/example/authsvc/infrastructure/security/jwt/TenantSlugResolver.java src/test/java/com/example/authsvc/infrastructure/security/jwt/TenantSlugResolverTest.java
git commit -m "remove cross-service DB query from TenantSlugResolver, extract platform tenant sentinel"
```

---

### Task 3: Configurable token delivery (cookie vs JSON body)

**Files:**
- Create: `src/main/java/com/example/authsvc/config/properties/AuthBehaviorProperties.java`
- Modify: `src/main/java/com/example/authsvc/api/dto/response/LoginResponse.java`
- Modify: `src/main/java/com/example/authsvc/api/dto/response/RefreshResponse.java`
- Modify: `src/main/java/com/example/authsvc/application/impl/RefreshTokenServiceImpl.java` (one call site of `new RefreshResponse(...)`)
- Modify: `src/main/java/com/example/authsvc/api/controller/AuthController.java`
- Modify: `src/main/resources/application.yaml`
- Test: `src/test/java/com/example/authsvc/api/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `LoginResult` / `RefreshResult` (existing, both already carry raw `accessToken()`/`refreshToken()` alongside the response DTO — see plan investigation notes, unchanged by this task).
- Produces: `AuthBehaviorProperties.isJsonTokenDelivery(): boolean` — consumed by `AuthController` only in this task, but also by Task 4/5/6 controller methods for consistency (a register/logout-all response doesn't carry tokens, so those tasks don't branch on it, only read `isRegistrationOpen()`/etc. — noted so Task 4 doesn't duplicate this class).

- [ ] **Step 1: Add the config properties class**

```java
// src/main/java/com/example/authsvc/config/properties/AuthBehaviorProperties.java
package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auth")
public class AuthBehaviorProperties {

    /** "open" (default) or "disabled" — gates POST /api/v1/auth/register. */
    private String registrationMode = "open";

    /** "cookie" (default, existing behavior) or "json" — response body includes raw tokens instead of Set-Cookie headers. */
    private String tokenDeliveryMode = "cookie";

    public boolean isRegistrationOpen() {
        return "open".equalsIgnoreCase(registrationMode);
    }

    public boolean isJsonTokenDelivery() {
        return "json".equalsIgnoreCase(tokenDeliveryMode);
    }
}
```

- [ ] **Step 2: Add env-overridable defaults to application.yaml**

The top-level `auth:` block starts at line 363 (`# CUSTOM AUTH CONFIG` section) with `session:` as its first child at 2-space indent. Add these two keys as siblings of `session:`, `refresh-token:`, `magic-link:`, etc. — same 2-space indent, anywhere in that block:

```yaml
  registration-mode: ${REGISTRATION_MODE:open}
  token-delivery-mode: ${TOKEN_DELIVERY_MODE:cookie}
```

This binds to `auth.registration-mode` / `auth.token-delivery-mode`, matching `AuthBehaviorProperties`'s `@ConfigurationProperties(prefix = "auth")` from Step 1 — the same pattern `SuperAdminProperties` already uses correctly (`@ConfigurationProperties(prefix = "auth.super-admin")` binding to the nested `auth.super-admin.*` yaml keys in this same block). Do **not** follow `CookieProperties`'s prefix pattern (`app.cookie`) as a reference — that one is a pre-existing bug in the fork: its yaml block is a bare top-level `cookie:` key (line 292), not nested under `app:`, so that properties class's fields never actually bind to the yaml and always fall back to their Java defaults. Out of scope to fix here; just don't copy it.

- [ ] **Step 3: Add refreshToken to LoginResponse**

```java
// src/main/java/com/example/authsvc/api/dto/response/LoginResponse.java — full file after this change
package com.example.authsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Schema(description = "Successful login response body")
public class LoginResponse {

    @Schema(description = "UUID of the authenticated user", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID userId;

    @Schema(description = "Email of the authenticated user", example = "user@cpms.com")
    private String email;

    @Schema(description = "UTC timestamp when the access token expires")
    private Instant accessTokenExpiresAt;

    @Schema(description = "JWT access token. Paste this value (without 'Bearer ') into Swagger's Authorize dialog "
            + "or use as 'Authorization: Bearer <token>' in Postman/curl. "
            + "Browsers should rely on the HttpOnly access_token cookie instead.")
    private String accessToken;

    @Schema(description = "Raw refresh token — only populated when auth.token-delivery-mode=json. "
            + "Cookie-mode responses never include this; the refresh token is HttpOnly-cookie-only.")
    private String refreshToken;

    // backward-compat constructor used by existing callers that don't yet pass the token
    public LoginResponse(UUID userId, String email, Instant accessTokenExpiresAt) {
        this.userId               = userId;
        this.email                = email;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public LoginResponse(UUID userId, String email, Instant accessTokenExpiresAt, String accessToken) {
        this.userId               = userId;
        this.email                = email;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
        this.accessToken          = accessToken;
    }

    public LoginResponse(UUID userId, String email, Instant accessTokenExpiresAt, String accessToken, String refreshToken) {
        this(userId, email, accessTokenExpiresAt, accessToken);
        this.refreshToken = refreshToken;
    }
}
```

- [ ] **Step 4: Add optional token fields to RefreshResponse**

```java
// src/main/java/com/example/authsvc/api/dto/response/RefreshResponse.java — full file after this change
package com.example.authsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response body returned after a successful token refresh")
public record RefreshResponse(
        @Schema(description = "UTC timestamp when the new access token expires")
        Instant accessTokenExpiresAt,

        @Schema(description = "JWT access token — only populated when auth.token-delivery-mode=json")
        String accessToken,

        @Schema(description = "Raw refresh token — only populated when auth.token-delivery-mode=json")
        String refreshToken
) {
    /** Backward-compat constructor for the existing cookie-mode call site — tokens travel via Set-Cookie, not this DTO. */
    public RefreshResponse(Instant accessTokenExpiresAt) {
        this(accessTokenExpiresAt, null, null);
    }
}
```

- [ ] **Step 5: Confirm the one existing call site still compiles unchanged**

No code change needed — `RefreshTokenServiceImpl.java:215`'s `new RefreshResponse(tokenPair.accessTokenExpiry())` now resolves to the new 1-arg backward-compat constructor added in Step 4. Just re-read that line to confirm it still says exactly that (don't modify it):

Run: `grep -n "new RefreshResponse(" src/main/java/com/example/authsvc/application/impl/RefreshTokenServiceImpl.java`
Expected: `new RefreshResponse(tokenPair.accessTokenExpiry()),` — unchanged.

- [ ] **Step 6: Branch AuthController's login/refresh on token-delivery-mode**

Add `AuthBehaviorProperties behaviorProps` to the constructor-injected fields (add the field, `@RequiredArgsConstructor` generates the constructor automatically — no manual constructor to edit). Then modify the two methods:

```java
// src/main/java/com/example/authsvc/api/controller/AuthController.java — add this field near the top of the class, alongside the existing five:
    private final AuthBehaviorProperties behaviorProps;
```

Add the import: `import com.example.authsvc.config.properties.AuthBehaviorProperties;`

```java
// Replace the body of the existing login() method with:
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        long startNs = System.nanoTime();

        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        log.debug("login.request email={} ip={}", request.getEmail(), ipAddress);

        LoginResult result = loginService.login(request, ipAddress, userAgent);

        long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
        log.info("login.response userId={} email={} latencyMs={}",
                result.response().getUserId(), result.response().getEmail(), latencyMs);
        log.info("perf.controller.login.ms={}", latencyMs);

        if (behaviorProps.isJsonTokenDelivery()) {
            LoginResponse body = new LoginResponse(
                    result.response().getUserId(),
                    result.response().getEmail(),
                    result.response().getAccessTokenExpiresAt(),
                    result.accessToken(),
                    result.refreshToken());
            return ResponseEntity.ok(body);
        }

        ResponseCookie accessCookie  = cookieFactory.createAccessTokenCookie(
                result.accessToken(),  result.accessTokenTtl());
        ResponseCookie refreshCookie = cookieFactory.createRefreshTokenCookie(
                result.refreshToken(), result.refreshTokenTtl());

        // Clear old cookies at legacy paths before issuing the new token.
        // Order matters: clear-cookies are sent FIRST so that any "last-writer-wins"
        // client (e.g. Postman) ends up with the new token, not an empty value.
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearOldNarrowRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }
```

```java
// Replace the body of the existing refresh() method with:
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = AuthCookieFactory.REFRESH_TOKEN_COOKIE, required = false)
            String rawRefreshToken,
            HttpServletRequest httpRequest) {

        long startMs = System.currentTimeMillis();
        String ip = httpRequest.getRemoteAddr();
        String ua = httpRequest.getHeader("User-Agent");

        RefreshResult result;
        try {
            result = refreshTokenService.refresh(rawRefreshToken, ip, ua);
        } catch (UnauthorizedException e) {
            log.debug("refresh.unauthorized ip={} — clearing cookies", ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessTokenCookie().toString())
                    .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshTokenCookie().toString())
                    .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                    .build();
        }
        log.info("perf.controller.refresh.ms={}", System.currentTimeMillis() - startMs);

        if (behaviorProps.isJsonTokenDelivery()) {
            RefreshResponse body = new RefreshResponse(
                    result.response().accessTokenExpiresAt(),
                    result.accessToken(),
                    result.refreshToken());
            return ResponseEntity.ok(body);
        }

        ResponseCookie accessCookie  = cookieFactory.createAccessTokenCookie(
                result.accessToken(), result.accessTokenTtl());
        ResponseCookie refreshCookie = cookieFactory.createRefreshTokenCookie(
                result.refreshToken(), result.refreshTokenTtl());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearOldNarrowRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }
```

- [ ] **Step 7: Write a controller-level test proving both modes**

This project has no existing `@WebMvcTest` controller tests to follow (all existing tests are plain Mockito unit tests on service classes) — use plain unit construction instead of a Spring test slice, consistent with the codebase's actual convention:

```java
// src/test/java/com/example/authsvc/api/controller/AuthControllerTest.java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.application.service.ChangePasswordService;
import com.example.authsvc.application.service.LoginService;
import com.example.authsvc.application.service.RefreshTokenService;
import com.example.authsvc.application.service.SessionService;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private LoginService loginService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SessionService sessionService;
    @Mock private ChangePasswordService changePasswordService;
    @Mock private AuthCookieFactory cookieFactory;
    @Mock private HttpServletRequest httpRequest;

    private AuthController controller(AuthBehaviorProperties props) {
        return new AuthController(
                loginService, refreshTokenService, sessionService, changePasswordService, cookieFactory, props);
    }

    private LoginResult sampleLoginResult() {
        LoginResponse response = new LoginResponse(
                UUID.randomUUID(), "user@example.com", Instant.now().plusSeconds(900));
        return new LoginResult(response, "access-token-value", "refresh-token-value",
                Duration.ofMinutes(15), Duration.ofDays(30));
    }

    @Test
    void jsonModeReturnsTokensInBodyNotCookies() {
        AuthBehaviorProperties props = new AuthBehaviorProperties();
        props.setTokenDeliveryMode("json");
        when(loginService.login(any(), any(), any())).thenReturn(sampleLoginResult());

        ResponseEntity<LoginResponse> result = controller(props).login(
                new com.example.authsvc.api.dto.request.LoginRequest(), httpRequest);

        assertEquals("access-token-value", result.getBody().getAccessToken());
        assertEquals("refresh-token-value", result.getBody().getRefreshToken());
        assertNull(result.getHeaders().get(HttpHeaders.SET_COOKIE));
    }

    @Test
    void cookieModeSetsCookiesAndOmitsRefreshTokenFromBody() {
        AuthBehaviorProperties props = new AuthBehaviorProperties(); // default = cookie
        when(loginService.login(any(), any(), any())).thenReturn(sampleLoginResult());
        when(cookieFactory.createAccessTokenCookie(any(), any()))
                .thenReturn(org.springframework.http.ResponseCookie.from("access_token", "x").build());
        when(cookieFactory.createRefreshTokenCookie(any(), any()))
                .thenReturn(org.springframework.http.ResponseCookie.from("refresh_token", "y").build());
        when(cookieFactory.clearLegacyRefreshTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("refresh_token", "").build());
        when(cookieFactory.clearOldNarrowRefreshTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("refresh_token", "").build());

        ResponseEntity<LoginResponse> result = controller(props).login(
                new com.example.authsvc.api.dto.request.LoginRequest(), httpRequest);

        assertNull(result.getBody().getRefreshToken());
        assertEquals(4, result.getHeaders().get(HttpHeaders.SET_COOKIE).size());
    }

    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
}
```

- [ ] **Step 8: Run test to verify it passes, then the full suite**

Run: `./gradlew test --tests "com.example.authsvc.api.controller.AuthControllerTest" --console=plain`
Expected: PASS (2 tests)

Run: `./gradlew compileJava compileTestJava test --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/example/authsvc/config/properties/AuthBehaviorProperties.java src/main/java/com/example/authsvc/api/dto/response/LoginResponse.java src/main/java/com/example/authsvc/api/dto/response/RefreshResponse.java src/main/java/com/example/authsvc/api/controller/AuthController.java src/main/resources/application.yaml src/test/java/com/example/authsvc/api/controller/AuthControllerTest.java
git commit -m "add configurable token-delivery-mode (cookie|json) instead of forcing cookies"
```

---

### Task 4: Public registration endpoint with an on/off toggle

**Files:**
- Create: `src/main/java/com/example/authsvc/common/exception/EmailAlreadyExistsException.java`
- Create: `src/main/java/com/example/authsvc/common/exception/RegistrationDisabledException.java`
- Create: `src/main/java/com/example/authsvc/api/dto/request/RegisterRequest.java`
- Create: `src/main/java/com/example/authsvc/api/dto/response/RegisterResponse.java`
- Create: `src/main/java/com/example/authsvc/application/service/RegisterService.java`
- Create: `src/main/java/com/example/authsvc/application/impl/RegisterServiceImpl.java`
- Modify: `src/main/java/com/example/authsvc/api/advice/GlobalExceptionHandler.java`
- Modify: `src/main/java/com/example/authsvc/api/controller/AuthController.java`
- Test: `src/test/java/com/example/authsvc/application/impl/RegisterServiceImplTest.java`

**Interfaces:**
- Consumes: `AuthUserJpaRepository` (existing, `findByEmailAndActiveTrue`, inherited `save`), `PasswordHasher.hash(CharSequence): String` (existing), `TenantConstants.PLATFORM_TENANT_ID` (Task 2).
- Produces: `RegisterService.register(String email, String password, UUID tenantId, UUID roleId): UUID` — `tenantId` and `roleId` may be null (null `tenantId` → use the platform sentinel; null `roleId` → user has no role yet). Consumed by Task 5's internal-create endpoint (with a real `roleId`), which reuses this exact method so the "only internal can set roleId" rule is enforced by which DTO/controller can call it, not by service-layer logic duplicated twice.

- [ ] **Step 1: Add the two new exceptions**

```java
// src/main/java/com/example/authsvc/common/exception/EmailAlreadyExistsException.java
package com.example.authsvc.common.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
```

```java
// src/main/java/com/example/authsvc/common/exception/RegistrationDisabledException.java
package com.example.authsvc.common.exception;

public class RegistrationDisabledException extends RuntimeException {
    public RegistrationDisabledException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Wire both into GlobalExceptionHandler**

Add these two handler methods to `GlobalExceptionHandler.java` (alongside the existing ones — add the two new imports too: `com.example.authsvc.common.exception.EmailAlreadyExistsException` and `com.example.authsvc.common.exception.RegistrationDisabledException`):

```java
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExists(EmailAlreadyExistsException ex,
                                                                    HttpServletRequest request) {
        log.info("auth.email_already_exists path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RegistrationDisabledException.class)
    public ResponseEntity<ErrorResponse> handleRegistrationDisabled(RegistrationDisabledException ex,
                                                                      HttpServletRequest request) {
        log.info("auth.registration_disabled path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(ex.getMessage()));
    }
```

- [ ] **Step 3: Add the request/response DTOs**

```java
// src/main/java/com/example/authsvc/api/dto/request/RegisterRequest.java
package com.example.authsvc.api.dto.request;

import com.example.authsvc.common.validation.ValidPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

// Deliberately has no roleId field — public self-registration can never grant
// elevated roles. Only RegisterServiceImpl.register()'s roleId parameter,
// called from the internal-only endpoint (Task 5), can set one.
@Data
@Schema(description = "Public self-registration request")
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @ValidPassword
    @Schema(example = "••••••••", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Schema(description = "Opaque tenant id; omit for single-tenant deployments (uses the platform sentinel)")
    private UUID tenantId;
}
```

```java
// src/main/java/com/example/authsvc/api/dto/response/RegisterResponse.java
package com.example.authsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Response body returned after successful registration")
public record RegisterResponse(
        @Schema(description = "UUID of the newly created user")
        UUID userId
) {}
```

- [ ] **Step 4: Add the service interface**

```java
// src/main/java/com/example/authsvc/application/service/RegisterService.java
package com.example.authsvc.application.service;

import java.util.UUID;

public interface RegisterService {

    /**
     * Creates a new user.
     *
     * @param email    must be unique (case as submitted — matches existing auth_users.email uniqueness)
     * @param password raw password, hashed before storage
     * @param tenantId nullable — null resolves to {@code TenantConstants.PLATFORM_TENANT_ID}
     * @param roleId   nullable — only ever non-null when called from the internal admin-provisioning endpoint
     * @return the new user's id
     * @throws com.example.authsvc.common.exception.EmailAlreadyExistsException if the email is already registered
     */
    UUID register(String email, String password, UUID tenantId, UUID roleId);
}
```

- [ ] **Step 5: Write the failing service test**

```java
// src/test/java/com/example/authsvc/application/impl/RegisterServiceImplTest.java
package com.example.authsvc.application.impl;

import com.example.authsvc.common.exception.EmailAlreadyExistsException;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterServiceImplTest {

    @Mock private AuthUserJpaRepository userRepo;
    @Mock private PasswordHasher passwordHasher;

    private RegisterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RegisterServiceImpl(userRepo, passwordHasher);
    }

    @Test
    void registersWithPlatformSentinelWhenTenantIdOmitted() {
        when(userRepo.findByEmailAndActiveTrue("user@example.com")).thenReturn(Optional.empty());
        when(passwordHasher.hash("password123")).thenReturn("hashed");

        UUID userId = service.register("user@example.com", "password123", null, null);

        assertNotNull(userId);
        ArgumentCaptor<AuthUserEntity> captor = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userRepo).save(captor.capture());
        AuthUserEntity saved = captor.getValue();
        assertEquals("user@example.com", saved.getEmail());
        assertEquals("hashed", saved.getPasswordHash());
        assertEquals(TenantConstants.PLATFORM_TENANT_ID, saved.getTenantId());
        assertEquals(UserType.TENANT_USER, saved.getUserType());
        assertNull(saved.getRoleId());
        assertEquals(userId, saved.getId());
    }

    @Test
    void registersWithSuppliedTenantIdAndRoleId() {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(userRepo.findByEmailAndActiveTrue("admin@example.com")).thenReturn(Optional.empty());
        when(passwordHasher.hash("password123")).thenReturn("hashed");

        service.register("admin@example.com", "password123", tenantId, roleId);

        ArgumentCaptor<AuthUserEntity> captor = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userRepo).save(captor.capture());
        assertEquals(tenantId, captor.getValue().getTenantId());
        assertEquals(roleId, captor.getValue().getRoleId());
    }

    @Test
    void rejectsDuplicateEmail() {
        when(userRepo.findByEmailAndActiveTrue("taken@example.com"))
                .thenReturn(Optional.of(AuthUserEntity.builder().id(UUID.randomUUID()).build()));

        assertThrows(EmailAlreadyExistsException.class,
                () -> service.register("taken@example.com", "password123", null, null));

        verify(userRepo, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.authsvc.application.impl.RegisterServiceImplTest" --console=plain`
Expected: FAIL to compile — `RegisterServiceImpl` doesn't exist yet.

- [ ] **Step 7: Implement RegisterServiceImpl**

```java
// src/main/java/com/example/authsvc/application/impl/RegisterServiceImpl.java
package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.RegisterService;
import com.example.authsvc.common.exception.EmailAlreadyExistsException;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegisterServiceImpl implements RegisterService {

    private final AuthUserJpaRepository userRepo;
    private final PasswordHasher passwordHasher;

    @Override
    @Transactional
    public UUID register(String email, String password, UUID tenantId, UUID roleId) {
        if (userRepo.findByEmailAndActiveTrue(email).isPresent()) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        AuthUserEntity user = AuthUserEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId != null ? tenantId : TenantConstants.PLATFORM_TENANT_ID)
                .email(email)
                .passwordHash(passwordHasher.hash(password))
                .userType(UserType.TENANT_USER)
                .roleId(roleId)
                .active(true)
                .build();

        userRepo.save(user);
        return user.getId();
    }
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew test --tests "com.example.authsvc.application.impl.RegisterServiceImplTest" --console=plain`
Expected: PASS (3 tests)

- [ ] **Step 9: Wire the public /register endpoint into AuthController**

Add two more constructor-injected fields (same `@RequiredArgsConstructor` pattern, no manual constructor edit needed):

```java
// add near the top of AuthController, alongside the existing fields:
    private final RegisterService   registerService;
```

(`behaviorProps` was already added in Task 3 — reused here, not re-added.)

Add the import: `import com.example.authsvc.application.service.RegisterService;`, `import com.example.authsvc.common.exception.RegistrationDisabledException;`, `import com.example.authsvc.api.dto.request.RegisterRequest;`, `import com.example.authsvc.api.dto.response.RegisterResponse;`, `import org.springframework.http.HttpStatus;` (may already be imported from Task 3).

```java
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (!behaviorProps.isRegistrationOpen()) {
            throw new RegistrationDisabledException("Self-registration is disabled");
        }
        UUID userId = registerService.register(
                request.getEmail(), request.getPassword(), request.getTenantId(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(userId));
    }
```

- [ ] **Step 10: Run the full suite**

Run: `./gradlew compileJava compileTestJava test --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/example/authsvc/common/exception/EmailAlreadyExistsException.java src/main/java/com/example/authsvc/common/exception/RegistrationDisabledException.java src/main/java/com/example/authsvc/api/dto/request/RegisterRequest.java src/main/java/com/example/authsvc/api/dto/response/RegisterResponse.java src/main/java/com/example/authsvc/application/service/RegisterService.java src/main/java/com/example/authsvc/application/impl/RegisterServiceImpl.java src/main/java/com/example/authsvc/api/advice/GlobalExceptionHandler.java src/main/java/com/example/authsvc/api/controller/AuthController.java src/test/java/com/example/authsvc/application/impl/RegisterServiceImplTest.java
git commit -m "add public self-registration endpoint with a registration-mode on/off toggle"
```

---

### Task 5: Internal admin-provisioning endpoint

**Files:**
- Create: `src/main/java/com/example/authsvc/api/dto/request/InternalCreateUserRequest.java`
- Modify: `src/main/java/com/example/authsvc/api/controller/InternalUserController.java`
- Test: `src/test/java/com/example/authsvc/api/controller/InternalUserControllerTest.java`

**Interfaces:**
- Consumes: `RegisterService.register(String, String, UUID, UUID)` (Task 4) — this time called with a real `roleId`.
- Produces: `POST /internal/auth/users`, gated by the existing `InternalTokenAuthFilter` (already protects this controller's `/revoke-sessions` route — no new security wiring needed, same class, same header).

- [ ] **Step 1: Add the request DTO (this one does have roleId — only reachable via the internal-secret-gated route)**

```java
// src/main/java/com/example/authsvc/api/dto/request/InternalCreateUserRequest.java
package com.example.authsvc.api.dto.request;

import com.example.authsvc.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class InternalCreateUserRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @ValidPassword
    private String password;

    private UUID tenantId;

    private UUID roleId;
}
```

- [ ] **Step 2: Write the failing controller test**

```java
// src/test/java/com/example/authsvc/api/controller/InternalUserControllerTest.java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.InternalCreateUserRequest;
import com.example.authsvc.application.impl.InternalSessionRevocationService;
import com.example.authsvc.application.service.RegisterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalUserControllerTest {

    @Mock private InternalSessionRevocationService revocationService;
    @Mock private RegisterService registerService;

    private InternalUserController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalUserController(revocationService, registerService);
    }

    @Test
    void createUserForwardsRoleIdToRegisterService() {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        InternalCreateUserRequest request = new InternalCreateUserRequest();
        request.setEmail("admin@example.com");
        request.setPassword("password123");
        request.setTenantId(tenantId);
        request.setRoleId(roleId);
        when(registerService.register("admin@example.com", "password123", tenantId, roleId))
                .thenReturn(newUserId);

        ResponseEntity<?> response = controller.createUser(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(registerService).register("admin@example.com", "password123", tenantId, roleId);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.authsvc.api.controller.InternalUserControllerTest" --console=plain`
Expected: FAIL to compile — `InternalUserController` has no `RegisterService` constructor argument or `createUser` method yet.

- [ ] **Step 4: Add the endpoint**

```java
// src/main/java/com/example/authsvc/api/controller/InternalUserController.java — full file after this change
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.InternalCreateUserRequest;
import com.example.authsvc.api.dto.response.RegisterResponse;
import com.example.authsvc.application.impl.InternalSessionRevocationService;
import com.example.authsvc.application.service.RegisterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal service-to-service endpoints for user lifecycle management.
 *
 * <p>Protected by {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter} —
 * callers must include {@code X-Internal-Secret: <INTERNAL_SERVICE_SECRET>} header.
 * These endpoints are not intended for external clients or self-service users.
 */
@Slf4j
@RestController
@RequestMapping("/internal/auth/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final InternalSessionRevocationService revocationService;
    private final RegisterService                  registerService;

    /**
     * Creates a user on behalf of a trusted internal caller. The only path that
     * can set {@code roleId} at creation time — public self-registration never can.
     */
    @PostMapping
    public ResponseEntity<RegisterResponse> createUser(@Valid @RequestBody InternalCreateUserRequest request) {
        UUID userId = registerService.register(
                request.getEmail(), request.getPassword(), request.getTenantId(), request.getRoleId());
        log.info("internal.user_created userId={} tenantId={}", userId, request.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(userId));
    }

    /**
     * Revokes all active sessions and refresh tokens for the given user.
     *
     * <p>Returns {@code 204 No Content} regardless of whether the user existed
     * or had any active sessions (idempotent).
     *
     * @param userId   the user whose sessions to revoke
     * @param tenantId tenant context (optional, used for audit logging)
     */
    @PostMapping("/{userId}/revoke-sessions")
    public ResponseEntity<Void> revokeSessions(
            @PathVariable UUID userId,
            @RequestParam(required = false) UUID tenantId) {
        log.info("internal.revoke_sessions userId={} tenantId={}", userId, tenantId);
        revocationService.revokeAllForUser(userId, tenantId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 5: Run test to verify it passes, then the full suite**

Run: `./gradlew test --tests "com.example.authsvc.api.controller.InternalUserControllerTest" --console=plain`
Expected: PASS (1 test)

Run: `./gradlew compileJava compileTestJava test --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/authsvc/api/dto/request/InternalCreateUserRequest.java src/main/java/com/example/authsvc/api/controller/InternalUserController.java src/test/java/com/example/authsvc/api/controller/InternalUserControllerTest.java
git commit -m "add internal admin-provisioning endpoint, the only path that can set roleId at creation"
```

---

### Task 6: Self-service logout-all

**Files:**
- Modify: `src/main/java/com/example/authsvc/api/controller/AuthController.java`
- Test: extend `src/test/java/com/example/authsvc/api/controller/AuthControllerTest.java` (created in Task 3)

**Interfaces:**
- Consumes: `InternalSessionRevocationService.revokeAllForUser(UUID userId, UUID tenantId): void` (existing, already transactional + idempotent — see class javadoc read during planning), `AuthenticatedUser.getUserId()/getTenantId()` (existing, already used identically by `changePassword()` in the same controller).
- Produces: `POST /api/v1/auth/logout-all`.

- [ ] **Step 1: Add the field and endpoint**

Add one more constructor-injected field to `AuthController` (same pattern as Tasks 3 and 4):

```java
    private final InternalSessionRevocationService revocationService;
```

Add the import: `import com.example.authsvc.application.impl.InternalSessionRevocationService;`, `import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;` (may already be imported), `import org.springframework.security.core.annotation.AuthenticationPrincipal;` (may already be imported from `changePassword()`).

```java
    @PostMapping("/logout-all")
    public ResponseEntity<LogoutResponse> logoutAll(
            @AuthenticationPrincipal AuthenticatedUser principal) {

        revocationService.revokeAllForUser(principal.getUserId(), principal.getTenantId());
        log.info("auth.logout_all userId={}", principal.getUserId());

        if (behaviorProps.isJsonTokenDelivery()) {
            return ResponseEntity.ok(LogoutResponse.success());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .body(LogoutResponse.success());
    }
```

- [ ] **Step 2: Write the failing test**

Add this test method to the existing `src/test/java/com/example/authsvc/api/controller/AuthControllerTest.java` (from Task 3) — add `@Mock private InternalSessionRevocationService revocationService;` to the class's mock fields, and pass `revocationService` as the last constructor argument in the `controller(...)` helper method (update that helper's `new AuthController(...)` call to include it):

```java
    @Test
    void logoutAllRevokesEverySessionForTheCallingUser() {
        AuthBehaviorProperties props = new AuthBehaviorProperties();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        com.example.authsvc.infrastructure.security.principal.AuthenticatedUser principal =
                new com.example.authsvc.infrastructure.security.principal.AuthenticatedUser(
                        userId, tenantId, "platform", java.util.List.of(), com.example.authsvc.domain.enums.UserType.TENANT_USER,
                        UUID.randomUUID().toString(), Instant.now().plusSeconds(900), UUID.randomUUID().toString());
        when(cookieFactory.clearAccessTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("access_token", "").build());
        when(cookieFactory.clearRefreshTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("refresh_token", "").build());
        when(cookieFactory.clearLegacyRefreshTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("refresh_token", "").build());

        controller(props).logoutAll(principal);

        verify(revocationService).revokeAllForUser(userId, tenantId);
    }
```

Add `import static org.mockito.Mockito.verify;` if not already present in that file.

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests "com.example.authsvc.api.controller.AuthControllerTest" --console=plain`
Expected: FAIL to compile — `AuthController` has no `logoutAll` method or `InternalSessionRevocationService` constructor argument yet, and the test's `controller(...)` helper doesn't pass one either until Step 1 lands.

- [ ] **Step 4: Run test to verify it passes, then the full suite**

Run: `./gradlew test --tests "com.example.authsvc.api.controller.AuthControllerTest" --console=plain`
Expected: PASS (3 tests total in this file — 2 from Task 3, 1 new)

Run: `./gradlew compileJava compileTestJava test --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/authsvc/api/controller/AuthController.java src/test/java/com/example/authsvc/api/controller/AuthControllerTest.java
git commit -m "add self-service logout-all, reusing the existing idempotent revocation service"
```

---

### Task 7: Manual verification against real Postgres + Redis

**Files:**
- Modify: `README.md` (or create one if the fork didn't bring one across — check first)

**Interfaces:**
- Consumes: nothing new — this proves Tasks 1-6 actually work end-to-end, especially Task 1's migration fix, which nothing in Tasks 2-6 exercises against a real database.

- [ ] **Step 1: Check whether a docker-compose file exists for local Postgres+Redis**

Run: `ls docker-compose.yml 2>/dev/null || echo "none"`
If none exists, create one matching the pattern used elsewhere in this project (Postgres 15 + Redis, see `PMP CANADA/docker-compose.yml` or `Gen_AUTH`'s own Node-era one for the exact shape — same two services, renamed database to whatever `application.yaml`'s `AUTH_DB_URL` expects).

- [ ] **Step 2: Boot Postgres+Redis, run the app, exercise every new/fixed piece**

```bash
docker-compose up -d
AUTH_DB_URL=jdbc:postgresql://localhost:5432/postgres \
AUTH_DB_USERNAME=postgres AUTH_DB_PASSWORD=postgres \
INTERNAL_SERVICE_SECRET=dev-secret \
SUPER_ADMIN_EMAIL=admin@example.com SUPER_ADMIN_PASSWORD='DevAdmin@123' \
JWT_ISSUER=genauth JWT_AUDIENCE=genauth \
./gradlew bootRun --console=plain
```

Expected: application starts cleanly — this is the real proof Task 1's migration fix works, since `flyway migrate` runs on every boot and would have failed loudly before that fix.

- [ ] **Step 3: Exercise the fixed/new endpoints**

```bash
# Register (registration-mode defaults to open)
curl -s -X POST localhost:8101/api/v1/auth/register \
  -H 'content-type: application/json' \
  -d '{"email":"test@example.com","password":"password123"}'
# Expected: 201, { "userId": "..." }

# Register again with the same email — must 409
curl -s -i -X POST localhost:8101/api/v1/auth/register \
  -H 'content-type: application/json' \
  -d '{"email":"test@example.com","password":"password123"}'
# Expected: HTTP/1.1 409

# Login (cookie mode, the default) — no tokens in body
curl -s -i -X POST localhost:8101/api/v1/auth/login \
  -H 'content-type: application/json' \
  -d '{"email":"test@example.com","password":"password123"}'
# Expected: Set-Cookie headers present, body has no refreshToken field populated

# Restart with TOKEN_DELIVERY_MODE=json and repeat login — tokens now in body
# Expected: response body's "refreshToken" field is populated, no Set-Cookie header

# logout-all — needs a valid access token from the login above in Authorization: Bearer
curl -s -i -X POST localhost:8101/api/v1/auth/logout-all \
  -H "Authorization: Bearer <accessToken from login>"
# Expected: 200
```

- [ ] **Step 4: Restart with REGISTRATION_MODE=disabled and confirm register is blocked**

```bash
# with REGISTRATION_MODE=disabled set, restart, then:
curl -s -i -X POST localhost:8101/api/v1/auth/register \
  -H 'content-type: application/json' \
  -d '{"email":"someone-else@example.com","password":"password123"}'
# Expected: HTTP/1.1 403
```

- [ ] **Step 5: Exercise the internal admin-create endpoint**

```bash
curl -s -X POST localhost:8101/internal/auth/users \
  -H 'content-type: application/json' \
  -H 'X-Internal-Secret: dev-secret' \
  -d '{"email":"admin-created@example.com","password":"password123","roleId":"11111111-1111-1111-1111-111111111111"}'
# Expected: 201, { "userId": "..." }

# Without the header — must fail
curl -s -i -X POST localhost:8101/internal/auth/users \
  -H 'content-type: application/json' \
  -d '{"email":"nope@example.com","password":"password123"}'
# Expected: HTTP/1.1 403 (from InternalTokenAuthFilter, not reaching the controller)
```

- [ ] **Step 6: Document all of this in README.md and commit**

Update (or create) `README.md` with: what's implemented, the new env vars (`REGISTRATION_MODE`, `TOKEN_DELIVERY_MODE`), and the manual verification steps above so the next person doesn't have to re-derive them.

```bash
git add README.md
git commit -m "document genericization changes and manual verification steps"
```

## Self-Review Notes

- **Spec coverage:** blocking migration fixed ✅ (Task 1), dormant reconciliation tool removed ✅ (Task 1), tenant-slug cross-service query removed ✅ (Task 2), configurable token delivery ✅ (Task 3), public register + toggle ✅ (Task 4), internal admin-create with roleId ✅ (Task 5), self-service logout-all ✅ (Task 6), real-infra verification ✅ (Task 7).
- **Placeholder scan:** none — every step has runnable code or an exact command.
- **Type consistency:** `RegisterService.register(String, String, UUID, UUID)` signature is defined once in Task 4 and called identically (same argument order) from both `AuthController.register()` (Task 4, `roleId=null`) and `InternalUserController.createUser()` (Task 5, real `roleId`) — verified by re-reading both call sites above before finalizing this plan.
- **Constructor injection ordering:** `AuthController` gains fields across three tasks (3: `behaviorProps`; 4: `registerService`; 6: `revocationService`) — each task's test `controller(...)` helper must be updated to match the current full constructor argument list at the time that task runs; Task 6's step 2 explicitly calls this out since it's the last to add a field.
