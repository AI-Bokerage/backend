# Client-Token, Service-Token, OTP, Users-Exists Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add four self-contained internal endpoints to gen-auth-starter — `GET /internal/auth/users/exists`, `POST /internal/service-token`, `POST /internal/otp/request` + `POST /internal/otp/verify`, `POST /v1/client-token` — closing the self-contained slice of gap.md §1 (CPMS-parity sub-project 2).

**Architecture:** Each endpoint reuses existing infrastructure (JWT issuance via `JwtUtils`/`JwtClaims`, `TenantSlugResolver`, `InternalTokenAuthFilter`/`InternalHmacAuthFilter`, `EmailService`, Redis-backed store pattern). No new cross-service HTTP clients, no new filters. `V1InternalTokenController` gets a second HMAC-guarded handler and its class-level gate is restructured to per-handler bean-presence checks.

**Tech Stack:** Spring Boot 4 / Spring Security 7, Spring Data JPA, Spring Data Redis (`StringRedisTemplate`), Lombok, JUnit 5 + Mockito + AssertJ, `ApplicationContextRunner` for conditional-bean tests.

**Spec:** `docs/superpowers/specs/2026-08-27-internal-token-endpoints-design.md`

## Global Constraints

- All new `/internal/**` endpoints ride the existing, unconditionally-registered `InternalTokenAuthFilter` (shared-secret `X-Internal-Secret` header) via the `/internal/**` path prefix already `permitAll()` in `SecurityConfig` — no filter or `SecurityConfig` path changes needed for tasks 1-3.
- `/v1/client-token` is HMAC-guarded (`InternalHmacAuthFilter`) — needs a new `permitAll()` entry in `SecurityConfig` and a `target-paths` fail-fast check, same as the existing `/v1/impersonation-token`.
- Every new feature (service-token, client-token, OTP) is gated behind its own `@ConditionalOnProperty`, off by default — matches this codebase's established pattern (`ImpersonationTokenServiceImpl`, `OAuthConfig`).
- `JwtClaims` record signature (do not change): `(UUID userId, UUID tenantId, String tenantSlug, List<UUID> roleIds, UserType userType, Instant issuedAt, Instant expiresAt, String sessionId, String jti)`.
- `TenantConstants.PLATFORM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000")` already exists — reuse it, do not redefine.
- `JwtUtils.generateAccessToken(JwtClaims claims)` — exact existing signature, reuse as-is.
- No Co-Authored-By trailer on any commit (repo convention for this effort).

---

### Task 1: `GET /internal/auth/users/exists`

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/repository/AuthUserJpaRepository.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/api/controller/InternalUserController.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/api/controller/InternalUserControllerTest.java`

**Interfaces:**
- Consumes: nothing new — `AuthUserJpaRepository` (existing `@Repository` interface, already injected nowhere in this controller yet — will be newly injected).
- Produces: `AuthUserJpaRepository.existsByEmail(String email): boolean`. `InternalUserController.exists(String email): ResponseEntity<Boolean>` mapped to `GET /internal/auth/users/exists`.

- [ ] **Step 1: Read the current test file to match its exact style**

Run: read `gen-auth-starter/src/test/java/com/example/authsvc/api/controller/InternalUserControllerTest.java` in full before writing new tests — it uses `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` directly on the controller (no MockMvc, no Spring context). Match that exact style; do not introduce MockMvc.

- [ ] **Step 2: Write the failing tests**

Add to `InternalUserControllerTest.java` (add `@Mock private AuthUserJpaRepository authUserJpaRepository;` to the existing mock fields, and pass it into whatever `@InjectMocks` constructor Mockito uses — if the controller currently has no `AuthUserJpaRepository` field, Step 3 will add one, and Mockito's `@InjectMocks` will pick it up automatically via constructor injection since the controller uses `@RequiredArgsConstructor`):

```java
    @Test
    void exists_emailHasAccount_returnsTrue() {
        when(authUserJpaRepository.existsByEmail("taken@example.com")).thenReturn(true);

        ResponseEntity<Boolean> response = controller.exists("taken@example.com");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isTrue();
    }

    @Test
    void exists_emailUnused_returnsFalse() {
        when(authUserJpaRepository.existsByEmail("free@example.com")).thenReturn(false);

        ResponseEntity<Boolean> response = controller.exists("free@example.com");

        assertThat(response.getBody()).isFalse();
    }
```

Adjust the exact field/variable names (`controller`, mock injection style) to match whatever the existing test file already uses for its `@InjectMocks` controller instance and other `@Mock` fields — read the file first (Step 1) and follow its established naming, don't invent new conventions.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :gen-auth-starter:test --tests "*InternalUserControllerTest*"` from the repo root (`C:\Users\naksh\Desktop\Metaupspace\Gen_MS\Gen_AUTH`).
Expected: FAIL — compile error, `existsByEmail` and `exists` don't exist yet.

- [ ] **Step 4: Add `existsByEmail` to the repository**

In `AuthUserJpaRepository.java`, add inside the interface (alongside the existing `findByEmailAndActiveTrue`):

```java
    boolean existsByEmail(String email);
```

- [ ] **Step 5: Add the `exists` endpoint to the controller**

In `InternalUserController.java`: add `import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;`, add a new `private final AuthUserJpaRepository authUserJpaRepository;` field (the class uses `@RequiredArgsConstructor`, so no constructor edit needed), and add:

```java
    /**
     * Checks whether any account (active or inactive) exists for the given
     * email — used by internal invite flows to reject duplicate invites
     * before sending them.
     */
    @GetMapping("/exists")
    public ResponseEntity<Boolean> exists(@RequestParam String email) {
        return ResponseEntity.ok(authUserJpaRepository.existsByEmail(email));
    }
```

Add `import org.springframework.web.bind.annotation.GetMapping;` and `import org.springframework.web.bind.annotation.RequestParam;` if not already present (`RequestParam` is already imported — used by `revokeSessions`; `GetMapping` is new).

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :gen-auth-starter:test --tests "*InternalUserControllerTest*"`
Expected: PASS, all tests green.

- [ ] **Step 7: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/repository/AuthUserJpaRepository.java gen-auth-starter/src/main/java/com/example/authsvc/api/controller/InternalUserController.java gen-auth-starter/src/test/java/com/example/authsvc/api/controller/InternalUserControllerTest.java
git commit -m "add GET /internal/auth/users/exists for invite-flow email dedup"
```

---

### Task 2: `POST /internal/service-token`

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/domain/TenantConstants.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/ServiceTokenResponse.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/service/ServiceTokenService.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ServiceTokenServiceImpl.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/controller/ServiceTokenController.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ServiceTokenServiceImplTest.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/config/ServiceTokenConfigTest.java`

**Interfaces:**
- Consumes: `JwtUtils.generateAccessToken(JwtClaims)` (existing), `TenantConstants.PLATFORM_TENANT_ID` (existing), `JwtClaims` record (existing, see Global Constraints), `UserType.SUPER_ADMIN` (existing enum constant).
- Produces: `TenantConstants.SERVICE_ACCOUNT_ID: UUID` (new constant, used by no other task). `ServiceTokenService.issue(String callerService): ServiceTokenResponse`. `ServiceTokenResponse(String accessToken, int expiresIn)`.

- [ ] **Step 1: Add the new constant**

In `TenantConstants.java`, add alongside `PLATFORM_TENANT_ID`:

```java
    /** Stable sentinel subject for tokens minted by {@code ServiceTokenServiceImpl} — not tied to any real user or tenant. */
    public static final UUID SERVICE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
```

- [ ] **Step 2: Write the response DTO**

`ServiceTokenResponse.java`:

```java
package com.example.authsvc.api.dto.response;

/**
 * Response from {@code POST /internal/service-token}.
 *
 * @param accessToken the signed JWT with {@code user_type=SUPER_ADMIN}, {@code sub=TenantConstants.SERVICE_ACCOUNT_ID}
 * @param expiresIn   token lifetime in seconds
 */
public record ServiceTokenResponse(
        String accessToken,
        int    expiresIn
) {}
```

- [ ] **Step 3: Write the service interface**

`ServiceTokenService.java`:

```java
package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.response.ServiceTokenResponse;

public interface ServiceTokenService {

    /**
     * Mints a short-lived, stateless service-account JWT for internal
     * service-to-service calls. No session is persisted.
     *
     * @param callerService the caller's own service name, for logging only —
     *                      never used as an authorization check
     */
    ServiceTokenResponse issue(String callerService);
}
```

- [ ] **Step 4: Write the failing test for the impl**

`ServiceTokenServiceImplTest.java`:

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.response.ServiceTokenResponse;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTokenServiceImplTest {

    @Mock private JwtUtils jwtUtils;

    @Test
    void issue_returnsFiveMinuteSuperAdminToken() {
        ServiceTokenServiceImpl service = new ServiceTokenServiceImpl(jwtUtils);
        setExpirationMinutes(service, 5);
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ServiceTokenResponse response = service.issue("sup-svc");

        assertThat(response.accessToken()).isEqualTo("signed-jwt");
        assertThat(response.expiresIn()).isEqualTo(5 * 60);

        ArgumentCaptor<JwtClaims> captor = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtUtils).generateAccessToken(captor.capture());
        JwtClaims claims = captor.getValue();
        assertThat(claims.userId()).isEqualTo(TenantConstants.SERVICE_ACCOUNT_ID);
        assertThat(claims.tenantId()).isEqualTo(TenantConstants.PLATFORM_TENANT_ID);
        assertThat(claims.tenantSlug()).isEqualTo("platform");
        assertThat(claims.roleIds()).isEmpty();
        assertThat(claims.userType()).isEqualTo(UserType.SUPER_ADMIN);
        assertThat(claims.sessionId()).isEqualTo("svc:sup-svc");
    }

    private static void setExpirationMinutes(ServiceTokenServiceImpl service, int value) {
        try {
            var field = ServiceTokenServiceImpl.class.getDeclaredField("expirationMinutes");
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "*ServiceTokenServiceImplTest*"`
Expected: FAIL — `ServiceTokenServiceImpl` doesn't exist yet.

- [ ] **Step 6: Write the impl**

`ServiceTokenServiceImpl.java`:

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.response.ServiceTokenResponse;
import com.example.authsvc.application.service.ServiceTokenService;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Issues stateless service-account JWTs for {@code POST /internal/service-token}.
 * Gated by {@code app.super-admin.enabled=true} — mints a {@code SUPER_ADMIN}-scoped
 * token, so it rides the same feature flag as {@link ImpersonationTokenServiceImpl}.
 * No session is persisted: purely a stateless mint for trusted internal callers.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
public class ServiceTokenServiceImpl implements ServiceTokenService {

    private final JwtUtils jwtUtils;

    @Value("${jwt.service-token.expiration-minutes:5}")
    private int expirationMinutes;

    public ServiceTokenServiceImpl(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public ServiceTokenResponse issue(String callerService) {
        Instant now       = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        JwtClaims claims = new JwtClaims(
                TenantConstants.SERVICE_ACCOUNT_ID,
                TenantConstants.PLATFORM_TENANT_ID,
                "platform",
                List.of(),
                UserType.SUPER_ADMIN,
                now,
                expiresAt,
                "svc:" + callerService,
                null
        );

        String accessToken = jwtUtils.generateAccessToken(claims);
        int expiresInSeconds = expirationMinutes * 60;
        log.info("service.token.issued caller={} expiresIn={}", callerService, expiresInSeconds);
        return new ServiceTokenResponse(accessToken, expiresInSeconds);
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "*ServiceTokenServiceImplTest*"`
Expected: PASS.

- [ ] **Step 8: Write the controller**

`ServiceTokenController.java`:

```java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.response.ServiceTokenResponse;
import com.example.authsvc.application.service.ServiceTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint for issuing stateless service-account JWTs. Only registered
 * when {@code app.super-admin.enabled=true}.
 *
 * <p>Protected by {@code X-Internal-Secret} via
 * {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter}
 * through the {@code /internal/**} path prefix — no bespoke auth here.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ServiceTokenController {

    private final ServiceTokenService serviceTokenService;

    @PostMapping("/service-token")
    public ResponseEntity<ServiceTokenResponse> issueServiceToken(
            @RequestHeader(value = "X-CPMS-Service", defaultValue = "unknown") String callerService) {
        return ResponseEntity.ok(serviceTokenService.issue(callerService));
    }
}
```

- [ ] **Step 9: Write the config test**

`ServiceTokenConfigTest.java`:

```java
package com.example.authsvc.config;

import com.example.authsvc.api.controller.ServiceTokenController;
import com.example.authsvc.application.impl.ServiceTokenServiceImpl;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ServiceTokenConfigTest {

    @Configuration
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JwtUtils.class, () -> mock(JwtUtils.class))
            .withUserConfiguration(ServiceTokenServiceImpl.class, ServiceTokenController.class);

    @Test
    void enabled_beansPresent() {
        contextRunner
                .withPropertyValues("app.super-admin.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ServiceTokenServiceImpl.class);
                    assertThat(context).hasSingleBean(ServiceTokenController.class);
                });
    }

    @Test
    void disabled_beansAbsent() {
        contextRunner
                .withPropertyValues("app.super-admin.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ServiceTokenServiceImpl.class);
                    assertThat(context).doesNotHaveBean(ServiceTokenController.class);
                });
    }
}
```

- [ ] **Step 10: Run all new tests, then the full module test suite**

Run: `./gradlew :gen-auth-starter:test --tests "*ServiceToken*"`
Expected: PASS.

Run: `./gradlew :gen-auth-starter:test`
Expected: BUILD SUCCESSFUL (confirms no regressions elsewhere).

- [ ] **Step 11: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/domain/TenantConstants.java gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/ServiceTokenResponse.java gen-auth-starter/src/main/java/com/example/authsvc/application/service/ServiceTokenService.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ServiceTokenServiceImpl.java gen-auth-starter/src/main/java/com/example/authsvc/api/controller/ServiceTokenController.java gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ServiceTokenServiceImplTest.java gen-auth-starter/src/test/java/com/example/authsvc/config/ServiceTokenConfigTest.java
git commit -m "add POST /internal/service-token stateless service-account JWT mint"
```

---

### Task 3: OTP request/verify

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/OtpRequestDto.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/OtpVerifyDto.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/OtpIssuedResponse.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/OtpVerifiedResponse.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/domain/port/OtpStore.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/cache/RedisOtpStore.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/service/OtpService.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/OtpServiceImpl.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/controller/OtpController.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/OtpConfig.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/service/EmailService.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/EmailProvider.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/SmtpEmailProvider.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/SesEmailProvider.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ProviderBackedEmailService.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/cache/RedisOtpStoreTest.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/OtpServiceImplTest.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/config/OtpConfigTest.java`

**Interfaces:**
- Consumes: `StringRedisTemplate` (existing Spring Data Redis bean, same one `RedisOAuthSignupChallengeStore` consumes — read `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/cache/RedisOAuthSignupChallengeStore.java` and `gen-auth-starter/src/main/java/com/example/authsvc/config/OAuthConfig.java` first to see exactly how that bean is registered/injected, and mirror it for `OtpConfig`).
- Produces: `OtpStore.issue(String codeHash, Duration ttl): UUID`. `OtpStore.verifyAndConsume(UUID otpId, String codeHash): boolean`. `OtpService.requestOtp(String toEmail, String purpose): UUID` (the otpId). `OtpService.verifyOtp(UUID otpId, String code): boolean`. `EmailService.sendOtpCode(String toEmail, String code, String purpose): void` (new method on an existing interface — task 4 does not touch `EmailService`, no conflict). `EmailProvider.sendOtpCode(String toEmail, String code, String purpose): void` (new method on an existing interface).

- [ ] **Step 1: Read the OAuth Redis-store registration pattern first**

Before writing any code, read `gen-auth-starter/src/main/java/com/example/authsvc/config/OAuthConfig.java` in full to see exactly how `RedisOAuthSignupChallengeStore` is registered as a `@Bean` (it is NOT a `@Component` — registered explicitly with an `ObjectMapper` configured with `JavaTimeModule`). `RedisOtpStore` has no JSON payload (it stores a plain hash string), so it does not need the `ObjectMapper` — but it still needs the same `StringRedisTemplate` injection style. Mirror the bean-registration *shape*, not the JSON parts.

- [ ] **Step 2: Write the failing test for `RedisOtpStore`**

`RedisOtpStoreTest.java` — use the same Mockito-based unit-test style `RedisOAuthSignupChallengeStore`'s own test file uses if one exists (check `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/cache/` for `RedisOAuthSignupChallengeStoreTest.java` and copy its mocking approach for `StringRedisTemplate`/`ValueOperations`). Write:

```java
package com.example.authsvc.infrastructure.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisOtpStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @Test
    void issue_storesHashWithTtl_returnsGeneratedId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisOtpStore store = new RedisOtpStore(redisTemplate);

        UUID otpId = store.issue("hash123", Duration.ofMinutes(5));

        assertThat(otpId).isNotNull();
        verify(valueOperations).set(eq("otp:" + otpId), eq("hash123"), eq(Duration.ofMinutes(5)));
    }

    @Test
    void verifyAndConsume_matchingHash_returnsTrueAndDeletes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID otpId = UUID.randomUUID();
        when(valueOperations.get("otp:" + otpId)).thenReturn("hash123");

        boolean result = new RedisOtpStore(redisTemplate).verifyAndConsume(otpId, "hash123");

        assertThat(result).isTrue();
        verify(redisTemplate).delete("otp:" + otpId);
    }

    @Test
    void verifyAndConsume_mismatchedHash_returnsFalseButStillDeletes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID otpId = UUID.randomUUID();
        when(valueOperations.get("otp:" + otpId)).thenReturn("hash123");

        boolean result = new RedisOtpStore(redisTemplate).verifyAndConsume(otpId, "wrong-hash");

        assertThat(result).isFalse();
        verify(redisTemplate).delete("otp:" + otpId);
    }

    @Test
    void verifyAndConsume_missingId_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        UUID otpId = UUID.randomUUID();
        when(valueOperations.get("otp:" + otpId)).thenReturn(null);

        boolean result = new RedisOtpStore(redisTemplate).verifyAndConsume(otpId, "any-hash");

        assertThat(result).isFalse();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "*RedisOtpStoreTest*"`
Expected: FAIL — `RedisOtpStore`/`OtpStore` don't exist yet.

- [ ] **Step 4: Write the `OtpStore` port**

`OtpStore.java`:

```java
package com.example.authsvc.domain.port;

import java.time.Duration;
import java.util.UUID;

public interface OtpStore {

    /** Generates a fresh id, stores the given code hash under it with the given TTL, and returns the id. */
    UUID issue(String codeHash, Duration ttl);

    /**
     * Atomically checks whether the stored hash for {@code otpId} matches
     * {@code codeHash}, then deletes the entry regardless of outcome —
     * always single-use, no replay even on a wrong-code attempt.
     */
    boolean verifyAndConsume(UUID otpId, String codeHash);
}
```

- [ ] **Step 5: Write the `RedisOtpStore` impl**

`RedisOtpStore.java`:

```java
package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.port.OtpStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Redis-backed {@link OtpStore}. Key schema: {@code otp:<otpId>} → the SHA-256
 * hex hash of the code (not JSON — nothing else to store), TTL from {@link #issue}.
 *
 * <p>{@code verifyAndConsume} does a plain GET + compare + DELETE (accepted
 * non-atomicity vs. a Lua script — a double-fire race only wastes one extra
 * failed attempt, not a security hole, since the value is deleted either way
 * after the first read completes).
 */
@RequiredArgsConstructor
public class RedisOtpStore implements OtpStore {

    static final String KEY_PREFIX = "otp:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public UUID issue(String codeHash, Duration ttl) {
        UUID otpId = UUID.randomUUID();
        redisTemplate.opsForValue().set(KEY_PREFIX + otpId, codeHash, ttl);
        return otpId;
    }

    @Override
    public boolean verifyAndConsume(UUID otpId, String codeHash) {
        String key = KEY_PREFIX + otpId;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            return false;
        }
        redisTemplate.delete(key);
        return Objects.equals(stored, codeHash);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "*RedisOtpStoreTest*"`
Expected: PASS.

- [ ] **Step 7: Add `sendOtpCode` to `EmailService` and `EmailProvider`**

In `EmailService.java`, add to the interface:

```java
    /**
     * Send a one-time-passcode to the given recipient.
     *
     * @param toEmail recipient email address
     * @param code    the plaintext OTP code
     * @param purpose caller-supplied label for what the code is for (e.g. "login", "withdrawal")
     */
    void sendOtpCode(String toEmail, String code, String purpose);
```

In `EmailProvider.java`, add the identical method signature to that interface too.

- [ ] **Step 8: Implement `sendOtpCode` in `SmtpEmailProvider`**

In `SmtpEmailProvider.java`, add a subject constant next to the existing ones:

```java
    static final String OTP_SUBJECT = "Your verification code";
```

Add the method, following the exact structure of `sendPasswordResetLink`:

```java
    @Override
    public void sendOtpCode(String toEmail, String code, String purpose) {
        log.info("email.send.started provider=smtp type=otp to=***");
        try {
            send(toEmail, OTP_SUBJECT, buildOtpHtml(code, purpose), buildOtpBody(code, purpose));
            log.info("email.send.success provider=smtp type=otp");
        } catch (Exception e) {
            log.error("email.send.failure provider=smtp type=otp reason={}", e.getMessage());
        }
    }

    private String buildOtpHtml(String code, String purpose) {
        return EmailHtmlTemplate.render(
                "Security",
                "Your verification code",
                null,
                "Use the code below to complete: " + purpose + ". This code is valid for a few minutes.",
                java.util.List.of(new EmailHtmlTemplate.InfoRow("Code", code)),
                null,
                null,
                "If you did not request this code, you can safely ignore this email.");
    }

    private String buildOtpBody(String code, String purpose) {
        return """
                Use the code below to complete: %s

                Code: %s

                This code is valid for a few minutes. If you did not request it, you can safely ignore this email.
                """.formatted(purpose, code);
    }
```

- [ ] **Step 9: Implement `sendOtpCode` in `SesEmailProvider`**

In `SesEmailProvider.java`, add:

```java
    private static final String OTP_SUBJECT = SmtpEmailProvider.OTP_SUBJECT;
```

alongside the existing `PASSWORD_RESET_SUBJECT`/`SUPER_ADMIN_BOOTSTRAP_SUBJECT` constants, then add the method (identical structure to `sendSuperAdminBootstrapCredentials`, reusing the same `buildOtpHtml`/`buildOtpBody` helper bodies as Step 8 — duplicate them here, this class doesn't share private methods with `SmtpEmailProvider`):

```java
    @Override
    public void sendOtpCode(String toEmail, String code, String purpose) {
        log.info("email.send.started provider=ses type=otp to=***");
        try {
            send(toEmail, OTP_SUBJECT, buildOtpHtml(code, purpose), buildOtpBody(code, purpose));
            log.info("email.send.success provider=ses type=otp");
        } catch (SesException e) {
            log.error("email.send.failure provider=ses type=otp reason={} awsError={}",
                    e.getMessage(), e.awsErrorDetails().errorCode());
        } catch (Exception e) {
            log.error("email.send.failure provider=ses type=otp reason={}", e.getMessage());
        }
    }

    private String buildOtpHtml(String code, String purpose) {
        return EmailHtmlTemplate.render(
                "Security",
                "Your verification code",
                null,
                "Use the code below to complete: " + purpose + ". This code is valid for a few minutes.",
                java.util.List.of(new EmailHtmlTemplate.InfoRow("Code", code)),
                null,
                null,
                "If you did not request this code, you can safely ignore this email.");
    }

    private String buildOtpBody(String code, String purpose) {
        return """
                Use the code below to complete: %s

                Code: %s

                This code is valid for a few minutes. If you did not request it, you can safely ignore this email.
                """.formatted(purpose, code);
    }
```

- [ ] **Step 10: Implement `sendOtpCode` in `ProviderBackedEmailService`**

In `ProviderBackedEmailService.java`, add:

```java
    @Override
    @Async("authAsync")
    public void sendOtpCode(String toEmail, String code, String purpose) {
        emailProvider.sendOtpCode(toEmail, code, purpose);
    }
```

- [ ] **Step 11: Compile-check the email stack**

Run: `./gradlew :gen-auth-starter:compileJava`
Expected: BUILD SUCCESSFUL — confirms all four `EmailService`/`EmailProvider` implementors now satisfy their interfaces.

- [ ] **Step 12: Write the OTP DTOs**

`OtpRequestDto.java`:

```java
package com.example.authsvc.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OtpRequestDto(
        @Email @NotBlank String toEmail,
        @NotBlank String purpose
) {}
```

`OtpVerifyDto.java`:

```java
package com.example.authsvc.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OtpVerifyDto(
        @NotNull UUID otpId,
        @NotBlank String code
) {}
```

`OtpIssuedResponse.java`:

```java
package com.example.authsvc.api.dto.response;

import java.util.UUID;

public record OtpIssuedResponse(UUID otpId) {}
```

`OtpVerifiedResponse.java`:

```java
package com.example.authsvc.api.dto.response;

public record OtpVerifiedResponse(boolean verified) {}
```

- [ ] **Step 13: Write the failing test for `OtpServiceImpl`**

`OtpServiceImplTest.java`:

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.domain.port.OtpStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.MessageDigest;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock private OtpStore otpStore;
    @Mock private EmailService emailService;

    @Test
    void requestOtp_storesHashNotPlaintext_emailsPlaintextCode() {
        OtpServiceImpl service = new OtpServiceImpl(otpStore, emailService);
        setTtlMinutes(service, 5);
        UUID expectedId = UUID.randomUUID();
        when(otpStore.issue(anyString(), eq(Duration.ofMinutes(5)))).thenReturn(expectedId);

        UUID otpId = service.requestOtp("user@example.com", "login");

        assertThat(otpId).isEqualTo(expectedId);

        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        verify(otpStore).issue(hashCaptor.capture(), eq(Duration.ofMinutes(5)));
        String storedHash = hashCaptor.getValue();

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpCode(eq("user@example.com"), codeCaptor.capture(), eq("login"));
        String plaintextCode = codeCaptor.getValue();

        assertThat(plaintextCode).hasSize(6).containsOnlyDigits();
        assertThat(storedHash).isNotEqualTo(plaintextCode);
        assertThat(storedHash).isEqualTo(sha256Hex(plaintextCode));
    }

    @Test
    void verifyOtp_delegatesHashedCodeToStore_singleCall() {
        OtpServiceImpl service = new OtpServiceImpl(otpStore, emailService);
        UUID otpId = UUID.randomUUID();
        when(otpStore.verifyAndConsume(eq(otpId), anyString())).thenReturn(true);

        boolean result = service.verifyOtp(otpId, "123456");

        assertThat(result).isTrue();
        verify(otpStore, times(1)).verifyAndConsume(eq(otpId), eq(sha256Hex("123456")));
    }

    @Test
    void verifyOtp_wrongCode_returnsFalse() {
        OtpServiceImpl service = new OtpServiceImpl(otpStore, emailService);
        UUID otpId = UUID.randomUUID();
        when(otpStore.verifyAndConsume(eq(otpId), anyString())).thenReturn(false);

        boolean result = service.verifyOtp(otpId, "000000");

        assertThat(result).isFalse();
        verify(emailService, never()).sendOtpCode(any(), any(), any());
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setTtlMinutes(OtpServiceImpl service, int value) {
        try {
            var field = OtpServiceImpl.class.getDeclaredField("ttlMinutes");
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 14: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "*OtpServiceImplTest*"`
Expected: FAIL — `OtpServiceImpl`/`OtpService` don't exist yet.

- [ ] **Step 15: Write the `OtpService` interface and impl**

`OtpService.java`:

```java
package com.example.authsvc.application.service;

import java.util.UUID;

public interface OtpService {

    /** Generates a code, stores its hash, emails the plaintext code, returns the new otpId. */
    UUID requestOtp(String toEmail, String purpose);

    /** Hashes the supplied code and atomically checks-and-consumes it against the stored entry. */
    boolean verifyOtp(UUID otpId, String code);
}
```

`OtpServiceImpl.java`:

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.application.service.OtpService;
import com.example.authsvc.domain.port.OtpStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * Issues and verifies caller-supplied-email one-time codes for
 * {@code POST /internal/otp/request} and {@code POST /internal/otp/verify}.
 * The plaintext code is never persisted — only its SHA-256 hash, via {@link OtpStore}.
 * Gated by {@code app.otp.enabled=true}.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.otp", name = "enabled", havingValue = "true")
public class OtpServiceImpl implements OtpService {

    private final OtpStore     otpStore;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.otp.expiration-minutes:5}")
    private int ttlMinutes;

    public OtpServiceImpl(OtpStore otpStore, EmailService emailService) {
        this.otpStore = otpStore;
        this.emailService = emailService;
    }

    @Override
    public UUID requestOtp(String toEmail, String purpose) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        String hash = sha256Hex(code);

        UUID otpId = otpStore.issue(hash, Duration.ofMinutes(ttlMinutes));
        emailService.sendOtpCode(toEmail, code, purpose);

        log.info("otp.requested otpId={} purpose={}", otpId, purpose);
        return otpId;
    }

    @Override
    public boolean verifyOtp(UUID otpId, String code) {
        boolean verified = otpStore.verifyAndConsume(otpId, sha256Hex(code));
        log.info("otp.verify.attempted otpId={} verified={}", otpId, verified);
        return verified;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 16: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "*OtpServiceImplTest*"`
Expected: PASS.

- [ ] **Step 17: Write the controller**

`OtpController.java`:

```java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.OtpRequestDto;
import com.example.authsvc.api.dto.request.OtpVerifyDto;
import com.example.authsvc.api.dto.response.OtpIssuedResponse;
import com.example.authsvc.api.dto.response.OtpVerifiedResponse;
import com.example.authsvc.application.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal caller-supplied-email OTP issue/verify endpoints. Only registered
 * when {@code app.otp.enabled=true}.
 *
 * <p>Protected by {@code X-Internal-Secret} via
 * {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter}
 * through the {@code /internal/**} path prefix.
 */
@Slf4j
@RestController
@RequestMapping("/internal/otp")
@ConditionalOnProperty(prefix = "app.otp", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/request")
    public ResponseEntity<OtpIssuedResponse> request(@Valid @RequestBody OtpRequestDto request) {
        return ResponseEntity.ok(new OtpIssuedResponse(otpService.requestOtp(request.toEmail(), request.purpose())));
    }

    @PostMapping("/verify")
    public ResponseEntity<OtpVerifiedResponse> verify(@Valid @RequestBody OtpVerifyDto request) {
        return ResponseEntity.ok(new OtpVerifiedResponse(otpService.verifyOtp(request.otpId(), request.code())));
    }
}
```

- [ ] **Step 18: Write `OtpConfig` to register `RedisOtpStore`**

First re-read `gen-auth-starter/src/main/java/com/example/authsvc/config/OAuthConfig.java` (from Step 1) for its exact `@Bean` method signature/annotation style for `RedisOAuthSignupChallengeStore`, then write `OtpConfig.java` mirroring it:

```java
package com.example.authsvc.config;

import com.example.authsvc.domain.port.OtpStore;
import com.example.authsvc.infrastructure.cache.RedisOtpStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@ConditionalOnProperty(prefix = "app.otp", name = "enabled", havingValue = "true")
public class OtpConfig {

    @Bean
    public OtpStore otpStore(StringRedisTemplate redisTemplate) {
        return new RedisOtpStore(redisTemplate);
    }
}
```

If `OAuthConfig`'s actual pattern differs meaningfully from this sketch (e.g. it registers the Redis store bean directly on `OAuthConfig` itself rather than a helper `@Bean` method, or uses a different `@ConditionalOnProperty` placement), follow what `OAuthConfig` actually does instead of this sketch — the goal is matching the established pattern exactly, not this plan's guess at it.

- [ ] **Step 19: Write the config test**

`OtpConfigTest.java`, matching `OAuthConfigTest`'s `ApplicationContextRunner` style:

```java
package com.example.authsvc.config;

import com.example.authsvc.api.controller.OtpController;
import com.example.authsvc.application.impl.OtpServiceImpl;
import com.example.authsvc.application.service.EmailService;
import com.example.authsvc.domain.port.OtpStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OtpConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
            .withBean(EmailService.class, () -> mock(EmailService.class))
            .withUserConfiguration(OtpConfig.class, OtpServiceImpl.class, OtpController.class);

    @Test
    void enabled_beansPresent() {
        contextRunner
                .withPropertyValues("app.otp.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OtpStore.class);
                    assertThat(context).hasSingleBean(OtpServiceImpl.class);
                    assertThat(context).hasSingleBean(OtpController.class);
                });
    }

    @Test
    void disabled_beansAbsent() {
        contextRunner
                .withPropertyValues("app.otp.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OtpStore.class);
                    assertThat(context).doesNotHaveBean(OtpServiceImpl.class);
                    assertThat(context).doesNotHaveBean(OtpController.class);
                });
    }
}
```

- [ ] **Step 20: Run all new tests, then the full module test suite**

Run: `./gradlew :gen-auth-starter:test --tests "*Otp*"`
Expected: PASS.

Run: `./gradlew :gen-auth-starter:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 21: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/OtpRequestDto.java gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/OtpVerifyDto.java gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/OtpIssuedResponse.java gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/OtpVerifiedResponse.java gen-auth-starter/src/main/java/com/example/authsvc/domain/port/OtpStore.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/cache/RedisOtpStore.java gen-auth-starter/src/main/java/com/example/authsvc/application/service/OtpService.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/OtpServiceImpl.java gen-auth-starter/src/main/java/com/example/authsvc/api/controller/OtpController.java gen-auth-starter/src/main/java/com/example/authsvc/config/OtpConfig.java gen-auth-starter/src/main/java/com/example/authsvc/application/service/EmailService.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/EmailProvider.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/SmtpEmailProvider.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email/provider/SesEmailProvider.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ProviderBackedEmailService.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/cache/RedisOtpStoreTest.java gen-auth-starter/src/test/java/com/example/authsvc/application/impl/OtpServiceImplTest.java gen-auth-starter/src/test/java/com/example/authsvc/config/OtpConfigTest.java
git commit -m "add POST /internal/otp/request + /internal/otp/verify caller-supplied-email OTP flow"
```

---

### Task 4: `POST /v1/client-token`

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/ClientTokenRequest.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/ClientTokenResponse.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/service/ClientTokenService.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ClientTokenServiceImpl.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/api/controller/V1InternalTokenController.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ClientTokenServiceImplTest.java`
- Modify: `gen-auth-starter/src/test/java/com/example/authsvc/api/controller/V1InternalTokenControllerConditionalRegistrationTest.java`

**Interfaces:**
- Consumes: `TenantSlugResolver.resolve(UUID): String` (existing), `AuthSessionJpaRepository.save(AuthSessionEntity): AuthSessionEntity` (existing, inherited from `JpaRepository`), `JwtUtils.generateAccessToken(JwtClaims): String` (existing), `InternalHmacAuthProperties.getTargetPaths(): List<String>` (existing).
- Produces: `ClientTokenService.issue(ClientTokenRequest): ClientTokenResponse`. `ClientTokenResponse(String accessToken, int expiresIn, String sessionId)`.

- [ ] **Step 1: Write the DTOs**

`ClientTokenRequest.java`:

```java
package com.example.authsvc.api.dto.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /v1/client-token}.
 *
 * <p>The calling internal service (e.g. CPT-SVC) is expected to have already
 * verified the human — this service is a pure token factory.
 */
public record ClientTokenRequest(
        @NotNull UUID clientUserId,
        @NotNull UUID tenantId,
        @Nullable String tenantSlug,
        @NotNull UUID roleId,
        @NotBlank String sessionId
) {}
```

`ClientTokenResponse.java`:

```java
package com.example.authsvc.api.dto.response;

public record ClientTokenResponse(
        String accessToken,
        int    expiresIn,
        String sessionId
) {}
```

- [ ] **Step 2: Write the service interface**

`ClientTokenService.java`:

```java
package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.request.ClientTokenRequest;
import com.example.authsvc.api.dto.response.ClientTokenResponse;

public interface ClientTokenService {
    ClientTokenResponse issue(ClientTokenRequest request);
}
```

- [ ] **Step 3: Write the failing test for the impl**

`ClientTokenServiceImplTest.java` — copy `ImpersonationTokenServiceImplTest`'s structure (already read in full during planning) and adapt field/type names:

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.ClientTokenRequest;
import com.example.authsvc.api.dto.response.ClientTokenResponse;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientTokenServiceImplTest {

    @Mock private JwtUtils                 jwtUtils;
    @Mock private AuthSessionJpaRepository authSessionRepository;
    @Mock private TenantSlugResolver       tenantSlugResolver;

    @Test
    void issue_noSlugProvided_resolvesViaFallbackAndPersistsSession() {
        ClientTokenServiceImpl service = new ClientTokenServiceImpl(jwtUtils, authSessionRepository, tenantSlugResolver);
        setExpirationMinutes(service, 15);

        UUID clientUserId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ClientTokenRequest request = new ClientTokenRequest(clientUserId, tenantId, null, roleId, "session-abc");

        ClientTokenResponse response = service.issue(request);

        assertThat(response.accessToken()).isEqualTo("signed-jwt");
        assertThat(response.expiresIn()).isEqualTo(15 * 60);
        assertThat(response.sessionId()).isEqualTo("session-abc");

        ArgumentCaptor<AuthSessionEntity> captor = ArgumentCaptor.forClass(AuthSessionEntity.class);
        verify(authSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserType()).isEqualTo(UserType.CLIENT);
        assertThat(captor.getValue().isImpersonation()).isFalse();
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void issue_slugProvided_skipsResolverFallback() {
        ClientTokenServiceImpl service = new ClientTokenServiceImpl(jwtUtils, authSessionRepository, tenantSlugResolver);
        setExpirationMinutes(service, 15);

        UUID clientUserId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ClientTokenRequest request = new ClientTokenRequest(clientUserId, tenantId, "acme", roleId, "session-abc");

        service.issue(request);

        verify(tenantSlugResolver, never()).resolve(any());
    }

    private static void setExpirationMinutes(ClientTokenServiceImpl service, int value) {
        try {
            var field = ClientTokenServiceImpl.class.getDeclaredField("expirationMinutes");
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "*ClientTokenServiceImplTest*"`
Expected: FAIL — `ClientTokenServiceImpl` doesn't exist yet.

- [ ] **Step 5: Write the impl**

`ClientTokenServiceImpl.java`:

```java
package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.ClientTokenRequest;
import com.example.authsvc.api.dto.response.ClientTokenResponse;
import com.example.authsvc.application.service.ClientTokenService;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Issues CLIENT-scoped JWTs for {@code POST /v1/client-token}. Gated by
 * {@code app.client-token.enabled=true}. Guarded by
 * {@link com.example.authsvc.infrastructure.security.filter.InternalHmacAuthFilter}
 * via {@code app.internal-hmac-auth.target-paths} — not by JWT, not by the
 * shared-secret filter.
 *
 * <p>The caller (e.g. CPT-SVC) is expected to have already verified the human
 * — this service is a token factory: it records a session locally and signs
 * a JWT, nothing more.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.client-token", name = "enabled", havingValue = "true")
public class ClientTokenServiceImpl implements ClientTokenService {

    private final JwtUtils                 jwtUtils;
    private final AuthSessionJpaRepository authSessionRepository;
    private final TenantSlugResolver       tenantSlugResolver;

    @Value("${jwt.client-token.expiration-minutes:15}")
    private int expirationMinutes;

    public ClientTokenServiceImpl(
            JwtUtils jwtUtils,
            AuthSessionJpaRepository authSessionRepository,
            TenantSlugResolver tenantSlugResolver) {
        this.jwtUtils = jwtUtils;
        this.authSessionRepository = authSessionRepository;
        this.tenantSlugResolver = tenantSlugResolver;
    }

    @Override
    @Transactional
    public ClientTokenResponse issue(ClientTokenRequest request) {
        Instant now       = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        String tenantSlug = (request.tenantSlug() != null && !request.tenantSlug().isBlank())
                ? request.tenantSlug()
                : tenantSlugResolver.resolve(request.tenantId());

        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(UUID.randomUUID())
                .userId(request.clientUserId())
                .tenantId(request.tenantId())
                .roleId(request.roleId())
                .userType(UserType.CLIENT)
                .impersonation(false)
                .active(true)
                .expiresAt(expiresAt)
                .lastActivityAt(now)
                .build();

        authSessionRepository.save(session);

        JwtClaims claims = new JwtClaims(
                request.clientUserId(),
                request.tenantId(),
                tenantSlug,
                List.of(request.roleId()),
                UserType.CLIENT,
                now,
                expiresAt,
                request.sessionId(),
                null
        );

        String accessToken = jwtUtils.generateAccessToken(claims);
        int expiresInSeconds = expirationMinutes * 60;
        log.info("client.token.issued clientUserId={} tenantId={} sessionId={} expiresIn={}",
                request.clientUserId(), request.tenantId(), request.sessionId(), expiresInSeconds);
        return new ClientTokenResponse(accessToken, expiresInSeconds, request.sessionId());
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "*ClientTokenServiceImplTest*"`
Expected: PASS.

- [ ] **Step 7: Restructure `V1InternalTokenController`**

Replace the full contents of `V1InternalTokenController.java` with:

```java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.ClientTokenRequest;
import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ClientTokenResponse;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.application.service.ClientTokenService;
import com.example.authsvc.application.service.ImpersonationTokenService;
import com.example.authsvc.config.properties.InternalHmacAuthProperties;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HMAC-guarded internal token-mint endpoints, matching CPMS auth-svc's
 * {@code /v1/*} path shape. Guarded by
 * {@link com.example.authsvc.infrastructure.security.filter.InternalHmacAuthFilter}
 * via {@code app.internal-hmac-auth.target-paths} — not by JWT, not by the
 * shared-secret filter. See {@code SecurityConfig}'s permitAll entries for
 * these paths.
 *
 * <p>The controller itself only requires {@code app.internal-hmac-auth.enabled=true}
 * — each handler independently requires its own underlying feature flag via its
 * service bean being present ({@code app.super-admin.enabled} for impersonation-token,
 * {@code app.client-token.enabled} for client-token). A handler whose backing
 * service is absent returns 404 rather than existing half-configured.
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@ConditionalOnProperty(prefix = "app.internal-hmac-auth", name = "enabled", havingValue = "true")
public class V1InternalTokenController {

    private final ImpersonationTokenService  impersonationTokenService;
    private final ClientTokenService         clientTokenService;
    private final InternalHmacAuthProperties internalHmacAuthProperties;

    public V1InternalTokenController(
            @Autowired(required = false) ImpersonationTokenService impersonationTokenService,
            @Autowired(required = false) ClientTokenService clientTokenService,
            InternalHmacAuthProperties internalHmacAuthProperties) {
        this.impersonationTokenService = impersonationTokenService;
        this.clientTokenService = clientTokenService;
        this.internalHmacAuthProperties = internalHmacAuthProperties;
    }

    @PostConstruct
    void assertGuardedByHmacFilter() {
        if (impersonationTokenService != null
                && !internalHmacAuthProperties.getTargetPaths().contains("/v1/impersonation-token")) {
            throw new IllegalStateException(
                    "app.internal-hmac-auth.target-paths must contain /v1/impersonation-token "
                            + "when app.internal-hmac-auth.enabled=true and app.super-admin.enabled=true — "
                            + "otherwise this endpoint is registered with no HMAC signature verification guarding it.");
        }
        if (clientTokenService != null
                && !internalHmacAuthProperties.getTargetPaths().contains("/v1/client-token")) {
            throw new IllegalStateException(
                    "app.internal-hmac-auth.target-paths must contain /v1/client-token "
                            + "when app.internal-hmac-auth.enabled=true and app.client-token.enabled=true — "
                            + "otherwise this endpoint is registered with no HMAC signature verification guarding it.");
        }
    }

    @PostMapping("/impersonation-token")
    public ResponseEntity<ImpersonationTokenResponse> issueImpersonationToken(
            @Valid @RequestBody ImpersonationTokenRequest request) {
        if (impersonationTokenService == null) {
            return ResponseEntity.notFound().build();
        }
        log.info("v1.impersonation.token.request superAdminId={} tenantId={} sessionId={}",
                request.superAdminId(), request.tenantId(), request.sessionId());
        return ResponseEntity.ok(impersonationTokenService.issue(request));
    }

    @PostMapping("/client-token")
    public ResponseEntity<ClientTokenResponse> issueClientToken(
            @Valid @RequestBody ClientTokenRequest request) {
        if (clientTokenService == null) {
            return ResponseEntity.notFound().build();
        }
        log.info("v1.client.token.request clientUserId={} tenantId={} sessionId={}",
                request.clientUserId(), request.tenantId(), request.sessionId());
        return ResponseEntity.ok(clientTokenService.issue(request));
    }
}
```

Note: this drops `@RequiredArgsConstructor` in favor of an explicit constructor — Lombok's `@RequiredArgsConstructor` cannot express per-parameter `@Autowired(required = false)`.

- [ ] **Step 8: Add `/v1/client-token` to `SecurityConfig`'s permitAll list**

In `SecurityConfig.java`, in the `POST` matcher list that currently ends with `"/v1/impersonation-token"` (around line 111), change:

```java
                                "/v1/impersonation-token"
                        ).permitAll()
```

to:

```java
                                "/v1/impersonation-token",
                                "/v1/client-token"
                        ).permitAll()
```

Also update the comment immediately above that block (currently reads `"Reachable only when both app.super-admin.enabled and app.internal-hmac-auth.enabled are true (V1InternalTokenController's own gate); 404s otherwise."`) to reflect the new per-handler gating:

```java
                                // HMAC-signature-authenticated, not JWT-authenticated — see
                                // InternalHmacAuthFilter. Each reachable only when
                                // app.internal-hmac-auth.enabled=true AND its own feature flag
                                // (app.super-admin.enabled / app.client-token.enabled) is true;
                                // 404s otherwise (V1InternalTokenController's per-handler gate).
```

- [ ] **Step 9: Update the existing conditional-registration test**

Rewrite `V1InternalTokenControllerConditionalRegistrationTest.java` in full — the controller's gating model changed from "whole controller present/absent on two flags" to "controller present whenever HMAC is on; each handler's backing service present/absent independently":

```java
package com.example.authsvc.api.controller;

import com.example.authsvc.application.service.ClientTokenService;
import com.example.authsvc.application.service.ImpersonationTokenService;
import com.example.authsvc.config.properties.InternalHmacAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class V1InternalTokenControllerConditionalRegistrationTest {

    private static InternalHmacAuthProperties propertiesWithPaths(String... paths) {
        InternalHmacAuthProperties properties = new InternalHmacAuthProperties();
        properties.setTargetPaths(List.of(paths));
        return properties;
    }

    @Test
    void hmacOff_controllerAbsent() {
        new ApplicationContextRunner()
                .withBean(ImpersonationTokenService.class, () -> mock(ImpersonationTokenService.class))
                .withBean(InternalHmacAuthProperties.class,
                        () -> propertiesWithPaths("/v1/impersonation-token"))
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(V1InternalTokenController.class));
    }

    @Test
    void hmacOnOnly_controllerPresentBothHandlersBackingBeansAbsent() {
        new ApplicationContextRunner()
                .withBean(InternalHmacAuthProperties.class, InternalHmacAuthProperties::new)
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(V1InternalTokenController.class);
                    assertThat(context).doesNotHaveBean(ImpersonationTokenService.class);
                    assertThat(context).doesNotHaveBean(ClientTokenService.class);
                });
    }

    @Test
    void hmacAndSuperAdminOn_impersonationTargetPathMissing_contextFailsToStart() {
        new ApplicationContextRunner()
                .withBean(ImpersonationTokenService.class, () -> mock(ImpersonationTokenService.class))
                .withBean(InternalHmacAuthProperties.class, InternalHmacAuthProperties::new) // empty
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void hmacAndClientTokenOn_clientTokenTargetPathMissing_contextFailsToStart() {
        new ApplicationContextRunner()
                .withBean(ClientTokenService.class, () -> mock(ClientTokenService.class))
                .withBean(InternalHmacAuthProperties.class, InternalHmacAuthProperties::new) // empty
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void hmacAndBothTokenServicesOn_bothTargetPathsPresent_contextStartsClean() {
        new ApplicationContextRunner()
                .withBean(ImpersonationTokenService.class, () -> mock(ImpersonationTokenService.class))
                .withBean(ClientTokenService.class, () -> mock(ClientTokenService.class))
                .withBean(InternalHmacAuthProperties.class,
                        () -> propertiesWithPaths("/v1/impersonation-token", "/v1/client-token"))
                .withUserConfiguration(V1InternalTokenController.class)
                .withPropertyValues("app.internal-hmac-auth.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(V1InternalTokenController.class));
    }
}
```

- [ ] **Step 10: Run the controller test, then the full module test suite**

Run: `./gradlew :gen-auth-starter:test --tests "*V1InternalTokenController*" --tests "*ClientToken*"`
Expected: PASS.

Run: `./gradlew :gen-auth-starter:test`
Expected: BUILD SUCCESSFUL — this is the task most likely to break something elsewhere (it changes `SecurityConfig` and an existing controller's constructor shape), so confirm the whole suite is still green, not just the new/changed tests.

- [ ] **Step 11: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/ClientTokenRequest.java gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/ClientTokenResponse.java gen-auth-starter/src/main/java/com/example/authsvc/application/service/ClientTokenService.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ClientTokenServiceImpl.java gen-auth-starter/src/main/java/com/example/authsvc/api/controller/V1InternalTokenController.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ClientTokenServiceImplTest.java gen-auth-starter/src/test/java/com/example/authsvc/api/controller/V1InternalTokenControllerConditionalRegistrationTest.java
git commit -m "add POST /v1/client-token, restructure V1InternalTokenController to per-handler gating"
```

---

## Post-plan note for the final whole-branch reviewer

- Confirm `README.md` documents the three new config flags (`app.super-admin.enabled` reused, `app.client-token.enabled`, `app.otp.enabled`) and the four new endpoints, following the exact pattern used when HMAC internal-auth was added (see the `172de84` merge commit's `README.md` diff for the format to match).
- Confirm `gen-auth-demo/src/main/resources/application.yaml` gets example entries for the new flags, mirroring how `app.internal-hmac-auth` was added there.
- These doc/demo-config updates were intentionally left out of the four tasks above (none of them are required for the code to work or for tests to pass) — fold them into whichever task's fix-wave or the final review, whichever this plan's executor judges cheaper.
