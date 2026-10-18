# OAuth Login (Spring Security OAuth2 Client) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add third-party login (any OIDC-compliant provider) to `gen-auth-starter`, gated by `app.oauth.enabled` (default `false`) — the last item on the punch list (Flyway → JWKS → MFA → **OAuth**).

**Architecture:** `build.gradle` already carries an unused `spring-boot-starter-oauth2-client` dependency. This plan wires it up via Spring Security's standard `oauth2Login()` instead of hand-rolling PKCE/discovery/JWKS/ID-token validation. Since `SecurityConfig` is `SessionCreationPolicy.STATELESS` (no `HttpSession`), the one genuinely new piece of infrastructure is a Redis-backed `AuthorizationRequestRepository` (replaces Spring's default session-backed one) plus a custom `AuthenticationSuccessHandler` that runs this service's own account-linking/blocking-gate logic and issues this service's own JWTs instead of a framework session. A new `auth_user_oauth_identity` table links `(provider, subject)` to `auth_users`; `password_hash` becomes nullable so an OAuth-only signup can exist before it sets one. The trickiest integration point — reusing the shared login critical path (session persistence, JWT generation, MFA gate) without re-verifying a password — is solved by extracting a `proceedPostAuthentication(user, ip, userAgent, start)` method out of `LoginExecutionServiceImpl.executeLogin(...)`, the same way the MFA slice split `issueTokens(...)` out for its own no-password paths.

**Tech Stack:** Spring Boot 4 / Spring Security 7 (`oauth2Login()`, `@ConditionalOnProperty` gating, JPA/Flyway, Redis via `StringRedisTemplate`), `OAuth2ClientJackson2Module` for Redis serialization of `OAuth2AuthorizationRequest`, JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-08-25-oauth-design.md`

## Global Constraints

- `app.oauth.enabled` defaults to `false`. When false: no OAuth beans registered (no `OAuthConfig` beans, no `OAuthController`), `SecurityConfig`'s `oauth2Login()` block is skipped entirely, zero required env vars, the new table exists but is unused.
- `app.oauth.enabled=true` **requires** at least one `spring.security.oauth2.client.registration.<id>.*` entry to be configured (standard Spring Boot property, not a custom one). If none is configured, `ClientRegistrationRepository` is never registered by Spring Boot and `OAuthConfig`'s `oAuth2AuthorizationRequestResolver` bean fails to construct — the app fails fast at startup with a clear Spring `NoSuchBeanDefinitionException`, not a silent no-op. This is intentional (matches this codebase's fail-fast posture for `MFA_SECRET_ENCRYPTION_KEY` etc.) — do not add a null-check workaround for it.
- Provider identity (issuer/client-id/client-secret/scopes) is **never** duplicated into a custom properties class — always configured through Spring Boot's own `spring.security.oauth2.client.registration.*` / `.provider.*` properties.
- OAuth applies the same way to every `UserType.TENANT_USER` login as password login does — `proceedPostAuthentication` runs the exact same `MfaLoginGate` check `executeLogin` already runs. Do not special-case OAuth to skip MFA.
- Package root stays `com.example.authsvc`. Migration file is `V6__oauth.sql` (last is `V5__mfa.sql`).
- No non-OIDC providers (GitHub etc.), no account-unlinking UI, no OAuth for the super-admin path, no persistence of provider access/refresh tokens — out of scope per the design spec.
- A brand-new account created via OAuth (`password_hash = NULL`) never receives real tokens until it completes `/api/v1/auth/oauth/complete-signup` — every code path that could issue tokens for such a user must go through the same blocking gate, never around it.
- Reuse existing patterns: `MfaChallengeStore`/`RedisMfaChallengeStore` for the Redis-backed opaque-token store shape, `MfaController`'s `buildSyntheticLoginRequest`/cookie-vs-JSON branching for response construction, `LoginExecutionServiceImpl`'s existing `@Autowired(required = false)` nullable-collaborator idiom for optional wiring.

---

### Task 1: Migration, `auth_user_oauth_identity` entity/repository, nullable `password_hash`

**Files:**
- Create: `gen-auth-starter/src/main/resources/db/migration/genauth/V6__oauth.sql`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/entity/AuthUserOAuthIdentityEntity.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/repository/AuthUserOAuthIdentityJpaRepository.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/entity/AuthUserEntity.java` (drop `nullable = false` on the `password_hash` column)
- Test: skipped for the same reason as the MFA slice's Task 1 — no Testcontainers-backed repository test exists elsewhere in this codebase for a plain CRUD repository. Exercised via Task 7's `OAuthLoginSuccessHandler` tests (mocked) and Task 9's manual verification (real Postgres).

**Interfaces:**
- Produces: `AuthUserOAuthIdentityEntity` (fields: `id`, `userId`, `provider` (`String`), `providerSubject` (`String`), `email` (`String`), `createdAt` (`Instant`, `@CreationTimestamp`)).
- Produces: `AuthUserOAuthIdentityJpaRepository.findByProviderAndProviderSubject(String provider, String providerSubject)` → `Optional<AuthUserOAuthIdentityEntity>`. Consumed by `OAuthLoginSuccessHandler` (Task 7).
- Modifies: `AuthUserEntity.passwordHash` — was `@Column(name = "password_hash", nullable = false, length = 255)`, becomes `@Column(name = "password_hash", length = 255)`. Every other field/annotation on `AuthUserEntity` is unchanged.

- [ ] **Step 1: Write the migration**

```sql
-- V6__oauth.sql
--
-- OAuth/OIDC login: one row per (provider, external subject) linking to an
-- auth_users row. password_hash becomes nullable — an account created purely
-- via OAuth has none until it completes the password-setup gate (see
-- OAuthLoginSuccessHandler / OAuthController.completeSignup in later tasks).

CREATE TABLE IF NOT EXISTS auth_user_oauth_identity (
    id                UUID                     PRIMARY KEY,
    user_id           UUID                     NOT NULL,
    provider          VARCHAR(50)              NOT NULL,
    provider_subject  VARCHAR(255)             NOT NULL,
    email             VARCHAR(255)             NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_auoi_provider_subject ON auth_user_oauth_identity (provider, provider_subject);
CREATE INDEX IF NOT EXISTS idx_auoi_user_id ON auth_user_oauth_identity (user_id);

ALTER TABLE auth_users ALTER COLUMN password_hash DROP NOT NULL;
```

- [ ] **Step 2: Write the entity**

```java
// AuthUserOAuthIdentityEntity.java
package com.example.authsvc.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code auth_user_oauth_identity} table — one row per
 * external identity (provider + subject) linked to an {@code auth_users}
 * row. {@code userId} is a FK-by-convention to {@code auth_users.id} (no
 * physical FK constraint, matching this codebase's existing cross-table
 * conventions, e.g. {@code AuthUserMfaEntity}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auth_user_oauth_identity")
public class AuthUserOAuthIdentityEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
```

- [ ] **Step 3: Write the repository**

```java
// AuthUserOAuthIdentityJpaRepository.java
package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.AuthUserOAuthIdentityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthUserOAuthIdentityJpaRepository extends JpaRepository<AuthUserOAuthIdentityEntity, UUID> {

    Optional<AuthUserOAuthIdentityEntity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
```

- [ ] **Step 4: Make `password_hash` nullable on the entity**

In `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/entity/AuthUserEntity.java`, change:

```java
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
```

to:

```java
    @Column(name = "password_hash", length = 255)
    private String passwordHash;
```

- [ ] **Step 5: Compile to verify no errors**

Run: `./gradlew :gen-auth-starter:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add gen-auth-starter/src/main/resources/db/migration/genauth/V6__oauth.sql gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/entity/AuthUserOAuthIdentityEntity.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/repository/AuthUserOAuthIdentityJpaRepository.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/entity/AuthUserEntity.java
git commit -m "add OAuth identity persistence; make password_hash nullable"
```

---

### Task 2: `LoginExecutionService.proceedPostAuthentication`

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/service/LoginExecutionService.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/LoginExecutionServiceImpl.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/LoginExecutionServiceImplTest.java` (add methods, existing file — do not remove any existing test)

**Interfaces:**
- Consumes: nothing new — pure refactor of existing code.
- Produces: `LoginExecutionService.proceedPostAuthentication(AuthUserEntity user, String ipAddress, String userAgent, long loginStart)` → `LoginResult`. Runs the MFA-gate-check-then-`issueTokens` tail that `executeLogin` already contains, minus password verification. Consumed by `OAuthLoginSuccessHandler` (Task 7) and `OAuthController.completeSignup` (Task 6).

- [ ] **Step 1: Write the failing tests**

Add these two methods to the existing `LoginExecutionServiceImplTest` class (same file, same `@BeforeEach setUp()`, same mocks already declared — do not duplicate field declarations):

```java
    @Test
    void proceedPostAuthentication_mfaGateReturnsChallenge_returnsChallengeResultWithoutIssuingTokens() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId).tenantId(tenantId)
                .email("oauth-user@example.com")
                .userType(UserType.TENANT_USER).active(true).build();

        com.example.authsvc.api.dto.response.MfaChallengeInfo challengeInfo =
                new com.example.authsvc.api.dto.response.MfaChallengeInfo("challenge-token-xyz", false);
        when(mfaLoginGate.checkAndIssueChallenge(eq(user), eq("1.2.3.4"), eq("curl/8.0")))
                .thenReturn(java.util.Optional.of(com.example.authsvc.api.dto.response.LoginResult.challenge(challengeInfo)));

        LoginResult result = service.proceedPostAuthentication(user, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        assertThat(result.mfaChallenge()).isNotNull();
        assertThat(result.mfaChallenge().challengeToken()).isEqualTo("challenge-token-xyz");
        assertThat(result.accessToken()).isNull();
        verify(jwtUtils, never()).generateTokenPair(any(JwtClaims.class));
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void proceedPostAuthentication_mfaGateReturnsEmpty_issuesRealTokensWithoutPasswordCheck() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId).tenantId(tenantId)
                .email("oauth-user2@example.com")
                .userType(UserType.TENANT_USER).active(true).build();

        when(authUserRoleRepository.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(mfaLoginGate.checkAndIssueChallenge(any(), anyString(), anyString())).thenReturn(java.util.Optional.empty());
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Instant now = Instant.now();
        when(jwtUtils.generateTokenPair(any(JwtClaims.class))).thenReturn(
                new TokenPair("access-token", "refresh-token", now.plusSeconds(900), now.plusSeconds(604800)));
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");

        LoginResult result = service.proceedPostAuthentication(user, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        assertThat(result.mfaChallenge()).isNull();
        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(passwordHasher, never()).verify(anyString(), anyString());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.application.impl.LoginExecutionServiceImplTest"`
Expected: FAIL (compilation error) — `proceedPostAuthentication` doesn't exist yet.

- [ ] **Step 3: Add the interface method**

In `LoginExecutionService.java`, add this method to the interface (alongside `executeLogin`/`issueTokens`/`handleFailure`):

```java
    /**
     * Runs the login critical path from the MFA decision onward (session
     * persistence, JWT generation, Redis token storage, async side-effects),
     * for a caller that has already authenticated the user by some means
     * other than password verification (OAuth login, or completing the
     * OAuth password-setup gate). Applies the exact same {@code MfaLoginGate}
     * check {@link #executeLogin} runs after password verification succeeds.
     *
     * @param user       pre-resolved, already-authenticated, active user entity
     * @param ipAddress  client IP address
     * @param userAgent  client User-Agent header value
     * @param loginStart {@code System.currentTimeMillis()} captured at the entry of
     *                   the calling handler, used for end-to-end latency logging
     * @return a {@link LoginResult} carrying the response body and token strings,
     *         or an MFA challenge if one is required
     */
    LoginResult proceedPostAuthentication(AuthUserEntity user, String ipAddress, String userAgent, long loginStart);
```

- [ ] **Step 4: Refactor `LoginExecutionServiceImpl`**

In `LoginExecutionServiceImpl.executeLogin(...)`, replace the MFA-decision-and-token-issuance tail:

```java
        // ── MFA decision (TENANT_USER only — super-admin logins never gate here) ─
        if (user.getUserType() == UserType.TENANT_USER && mfaLoginGate != null) {
            var challenge = mfaLoginGate.checkAndIssueChallenge(user, ipAddress, userAgent);
            if (challenge.isPresent()) {
                return challenge.get();
            }
        }

        return issueTokens(user, request, ipAddress, userAgent, loginStart);
    }
```

with:

```java
        return proceedPostAuthentication(user, ipAddress, userAgent, loginStart);
    }

    @Override
    public LoginResult proceedPostAuthentication(AuthUserEntity user, String ipAddress, String userAgent, long loginStart) {
        if (user.getUserType() == UserType.TENANT_USER && mfaLoginGate != null) {
            var challenge = mfaLoginGate.checkAndIssueChallenge(user, ipAddress, userAgent);
            if (challenge.isPresent()) {
                return challenge.get();
            }
        }
        return issueTokens(user, buildSyntheticLoginRequest(user), ipAddress, userAgent, loginStart);
    }

    private LoginRequest buildSyntheticLoginRequest(AuthUserEntity user) {
        // issueTokens(...) reads request.getEmail() nowhere in its body — a bare
        // LoginRequest carrying just the email keeps the method signature
        // unchanged without requiring a second overload. Same trick
        // MfaController.buildSyntheticLoginRequest already uses.
        LoginRequest synthetic = new LoginRequest();
        synthetic.setEmail(user.getEmail());
        return synthetic;
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.application.impl.LoginExecutionServiceImplTest"`
Expected: PASS (all existing tests plus the two new ones)

- [ ] **Step 6: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/application/service/LoginExecutionService.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/LoginExecutionServiceImpl.java gen-auth-starter/src/test/java/com/example/authsvc/application/impl/LoginExecutionServiceImplTest.java
git commit -m "extract LoginExecutionService.proceedPostAuthentication for no-password login paths"
```

---

### Task 3: `OAuthSignupChallengeStore` (Redis-backed)

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/domain/model/OAuthSignupChallengeEntry.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/domain/port/OAuthSignupChallengeStore.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/cache/RedisOAuthSignupChallengeStore.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/cache/RedisOAuthSignupChallengeStoreTest.java`

**Interfaces:**
- Produces: `OAuthSignupChallengeEntry(UUID userId, int attempts)` (record). `OAuthSignupChallengeStore` port: `String issue(UUID userId, Duration ttl)` → raw opaque token; `Optional<OAuthSignupChallengeEntry> find(String token)`; `void incrementAttempts(String token)`; `void delete(String token)`. Consumed by `OAuthLoginSuccessHandler` (Task 7) and `OAuthController` (Task 6).
- Consumes: nothing from earlier tasks — standalone Redis wrapper, same shape as `RedisMfaChallengeStore`.

This mirrors `MfaChallengeStore`/`RedisMfaChallengeStore` exactly, minus the `enrollmentRequired` field (not needed here — every entry in this store represents exactly one thing: "this user needs to set a password before they get real tokens").

- [ ] **Step 1: Write the failing test**

```java
package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.model.OAuthSignupChallengeEntry;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisOAuthSignupChallengeStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private OAuthSignupChallengeStore store;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        store = new RedisOAuthSignupChallengeStore(redisTemplate, mapper);
    }

    @Test
    void issue_storesEntryAndReturnsNonBlankToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        UUID userId = UUID.randomUUID();

        String token = store.issue(userId, Duration.ofMinutes(10));

        assertThat(token).isNotBlank();
        verify(valueOps).set(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10)));
    }

    @Test
    void find_unknownToken_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(any())).thenReturn(null);

        Optional<OAuthSignupChallengeEntry> result = store.find("unknown-token");

        assertThat(result).isEmpty();
    }

    @Test
    void issueThenFind_roundTrips() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        UUID userId = UUID.randomUUID();
        Map<String, String> fakeRedis = new HashMap<>();

        doAnswer(inv -> {
            fakeRedis.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        when(valueOps.get(any())).thenAnswer(inv -> fakeRedis.get((String) inv.getArgument(0)));

        String token = store.issue(userId, Duration.ofMinutes(10));
        Optional<OAuthSignupChallengeEntry> found = store.find(token);

        assertThat(found).isPresent();
        assertThat(found.get().userId()).isEqualTo(userId);
        assertThat(found.get().attempts()).isZero();
    }

    @Test
    void incrementAttempts_bumpsAttemptCount() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        UUID userId = UUID.randomUUID();
        Map<String, String> fakeRedis = new HashMap<>();

        doAnswer(inv -> {
            fakeRedis.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        when(valueOps.get(any())).thenAnswer(inv -> fakeRedis.get((String) inv.getArgument(0)));
        when(redisTemplate.getExpire(anyString())).thenReturn(600L);

        String token = store.issue(userId, Duration.ofMinutes(10));
        store.incrementAttempts(token);

        assertThat(store.find(token).get().attempts()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.cache.RedisOAuthSignupChallengeStoreTest"`
Expected: FAIL (compilation error) — none of the classes exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
// OAuthSignupChallengeEntry.java
package com.example.authsvc.domain.model;

import java.util.UUID;

public record OAuthSignupChallengeEntry(
        UUID userId,
        int attempts
) {}
```

```java
// OAuthSignupChallengeStore.java
package com.example.authsvc.domain.port;

import com.example.authsvc.domain.model.OAuthSignupChallengeEntry;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface OAuthSignupChallengeStore {

    /** Generates a fresh opaque token, stores the entry, and returns the raw token. */
    String issue(UUID userId, Duration ttl);

    Optional<OAuthSignupChallengeEntry> find(String token);

    void incrementAttempts(String token);

    void delete(String token);
}
```

```java
// RedisOAuthSignupChallengeStore.java
package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.model.OAuthSignupChallengeEntry;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed {@link OAuthSignupChallengeStore}.
 *
 * <p>Key schema: {@code oauth:signup:<token>} → {@link OAuthSignupChallengeEntry}
 * JSON (TTL = 10 minutes per the design spec). Mirrors
 * {@link RedisMfaChallengeStore} exactly.
 *
 * <p>Registered as a {@code @Bean} in {@code OAuthConfig} (Task 8) — not
 * annotated with {@code @Component} — so the {@code ObjectMapper} is
 * constructed with explicit {@code JavaTimeModule} settings, same as
 * {@code RedisMfaChallengeStore}.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisOAuthSignupChallengeStore implements OAuthSignupChallengeStore {

    static final String CHALLENGE_KEY_PREFIX = "oauth:signup:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;
    private final SecureRandom        random = new SecureRandom();

    @Override
    public String issue(UUID userId, Duration ttl) {
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        OAuthSignupChallengeEntry entry = new OAuthSignupChallengeEntry(userId, 0);
        String key = CHALLENGE_KEY_PREFIX + token;
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(entry), ttl);
        } catch (JsonProcessingException e) {
            log.error("oauth_signup_challenge.redis.serialize_failed userId={} reason={}", userId, e.getMessage());
            throw new IllegalStateException("Failed to serialize OAuthSignupChallengeEntry", e);
        }
        log.debug("oauth_signup_challenge.redis.issued userId={} ttlSeconds={}", userId, ttl.toSeconds());
        return token;
    }

    @Override
    public Optional<OAuthSignupChallengeEntry> find(String token) {
        String json = redisTemplate.opsForValue().get(CHALLENGE_KEY_PREFIX + token);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, OAuthSignupChallengeEntry.class));
        } catch (JsonProcessingException e) {
            log.error("oauth_signup_challenge.redis.deserialize_failed reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void incrementAttempts(String token) {
        find(token).ifPresent(entry -> {
            OAuthSignupChallengeEntry updated = new OAuthSignupChallengeEntry(entry.userId(), entry.attempts() + 1);
            String key = CHALLENGE_KEY_PREFIX + token;
            Long ttl = redisTemplate.getExpire(key);
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(updated),
                        Duration.ofSeconds(ttl != null && ttl > 0 ? ttl : 1));
            } catch (JsonProcessingException e) {
                log.error("oauth_signup_challenge.redis.serialize_failed reason={}", e.getMessage());
            }
        });
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(CHALLENGE_KEY_PREFIX + token);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.cache.RedisOAuthSignupChallengeStoreTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/domain/model/OAuthSignupChallengeEntry.java gen-auth-starter/src/main/java/com/example/authsvc/domain/port/OAuthSignupChallengeStore.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/cache/RedisOAuthSignupChallengeStore.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/cache/RedisOAuthSignupChallengeStoreTest.java
git commit -m "add Redis-backed OAuthSignupChallengeStore for blocking password-setup gate"
```

---

### Task 4: `RedisOAuth2AuthorizationRequestRepository`

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/oauth/RedisOAuth2AuthorizationRequestRepository.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/oauth/RedisOAuth2AuthorizationRequestRepositoryTest.java`

**Interfaces:**
- Produces: `RedisOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest>` (Spring Security's own interface — `loadAuthorizationRequest`, `saveAuthorizationRequest`, `removeAuthorizationRequest`). Also exposes `public static final String REQUEST_ATTRIBUTE_NAME` — the `HttpServletRequest` attribute key `removeAuthorizationRequest` stashes the just-removed request under, so `OAuthLoginSuccessHandler` (Task 7) can read the `tenantId` additional-parameter back out of it later in the same request lifecycle.
- Consumes: nothing from earlier tasks — standalone Redis wrapper, replaces Spring's default `HttpSessionOAuth2AuthorizationRequestRepository` (this service is stateless, no `HttpSession`).

- [ ] **Step 1: Write the failing test**

```java
package com.example.authsvc.infrastructure.security.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.jackson2.OAuth2ClientJackson2Module;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisOAuth2AuthorizationRequestRepositoryTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private AuthorizationRequestRepository<OAuth2AuthorizationRequest> repository;
    private final Map<String, String> fakeRedis = new HashMap<>();

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new OAuth2ClientJackson2Module());
        repository = new RedisOAuth2AuthorizationRequestRepository(redisTemplate, mapper);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doAnswer(inv -> fakeRedis.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOps).set(anyString(), anyString(), any(java.time.Duration.class));
        when(valueOps.get(any())).thenAnswer(inv -> fakeRedis.get((String) inv.getArgument(0)));
    }

    private OAuth2AuthorizationRequest testRequest(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("test-client-id")
                .redirectUri("https://app.example.com/login/oauth2/code/google")
                .scopes(Set.of("openid", "email"))
                .state(state)
                .build();
    }

    @Test
    void saveThenLoad_roundTrips() {
        OAuth2AuthorizationRequest original = testRequest("state-abc");
        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(original, saveRequest, saveResponse);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setParameter("state", "state-abc");

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("state-abc");
        assertThat(loaded.getClientId()).isEqualTo("test-client-id");
    }

    @Test
    void loadAuthorizationRequest_unknownState_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", "never-saved");

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void removeAuthorizationRequest_deletesFromRedisAndStashesOnRequestAttribute() {
        OAuth2AuthorizationRequest original = testRequest("state-xyz");
        repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), new MockHttpServletResponse());

        MockHttpServletRequest removeRequest = new MockHttpServletRequest();
        removeRequest.setParameter("state", "state-xyz");
        MockHttpServletResponse removeResponse = new MockHttpServletResponse();

        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(removeRequest, removeResponse);

        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo("state-xyz");
        assertThat(removeRequest.getAttribute(RedisOAuth2AuthorizationRequestRepository.REQUEST_ATTRIBUTE_NAME))
                .isSameAs(removed);
        assertThat(fakeRedis).doesNotContainKey("oauth2:authreq:state-xyz");
    }

    @Test
    void removeAuthorizationRequest_unknownState_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", "never-saved");

        assertThat(repository.removeAuthorizationRequest(request, new MockHttpServletResponse())).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.security.oauth.RedisOAuth2AuthorizationRequestRepositoryTest"`
Expected: FAIL (compilation error) — class doesn't exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.authsvc.infrastructure.security.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.time.Duration;

/**
 * Redis-backed replacement for Spring Security's default
 * {@code HttpSessionOAuth2AuthorizationRequestRepository} — this service runs
 * {@code SessionCreationPolicy.STATELESS} (see {@code SecurityConfig}), so
 * there is no {@code HttpSession} to stash the in-flight
 * {@link OAuth2AuthorizationRequest} (which already carries Spring's
 * generated {@code state} and PKCE {@code code_verifier}) in.
 *
 * <p>Key schema: {@code oauth2:authreq:<state>} → JSON via
 * {@code OAuth2ClientJackson2Module}, 10-minute TTL, deleted on
 * {@link #removeAuthorizationRequest} (one-shot, prevents replay).
 *
 * <p>{@link #removeAuthorizationRequest} additionally stashes the removed
 * request on a request attribute ({@link #REQUEST_ATTRIBUTE_NAME}) — Spring
 * Security's {@code OAuth2LoginAuthenticationFilter} calls this method during
 * callback processing, before invoking the success handler, and the
 * resulting {@code OAuth2AuthenticationToken} the success handler receives
 * carries no reference back to the original authorization request. Reading
 * this attribute is how {@code OAuthLoginSuccessHandler} (Task 7) recovers
 * the {@code tenantId} that {@link TenantAwareOAuth2AuthorizationRequestResolver}
 * (Task 5) captured at {@code /oauth2/authorization/{registrationId}} time.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String KEY_PREFIX = "oauth2:authreq:";
    public static final String REQUEST_ATTRIBUTE_NAME = "oauth2AuthorizationRequest";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String STATE_PARAM = "state";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(STATE_PARAM);
        return state == null ? null : read(state);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                          HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            String state = request.getParameter(STATE_PARAM);
            if (state != null) {
                redisTemplate.delete(KEY_PREFIX + state);
            }
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(authorizationRequest);
            redisTemplate.opsForValue().set(KEY_PREFIX + authorizationRequest.getState(), json, TTL);
        } catch (JsonProcessingException e) {
            log.error("oauth2.authreq.redis.serialize_failed reason={}", e.getMessage());
            throw new IllegalStateException("Failed to serialize OAuth2AuthorizationRequest", e);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        String state = request.getParameter(STATE_PARAM);
        if (state == null) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = read(state);
        if (authorizationRequest != null) {
            redisTemplate.delete(KEY_PREFIX + state);
            request.setAttribute(REQUEST_ATTRIBUTE_NAME, authorizationRequest);
        }
        return authorizationRequest;
    }

    private OAuth2AuthorizationRequest read(String state) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + state);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OAuth2AuthorizationRequest.class);
        } catch (JsonProcessingException e) {
            log.error("oauth2.authreq.redis.deserialize_failed reason={}", e.getMessage());
            return null;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.security.oauth.RedisOAuth2AuthorizationRequestRepositoryTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/oauth/RedisOAuth2AuthorizationRequestRepository.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/oauth/RedisOAuth2AuthorizationRequestRepositoryTest.java
git commit -m "add Redis-backed AuthorizationRequestRepository for stateless OAuth2 login"
```

---

### Task 5: `TenantAwareOAuth2AuthorizationRequestResolver`

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/oauth/TenantAwareOAuth2AuthorizationRequestResolver.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/oauth/TenantAwareOAuth2AuthorizationRequestResolverTest.java`

**Interfaces:**
- Produces: `TenantAwareOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver`, constructed with `(ClientRegistrationRepository)`. Wraps `DefaultOAuth2AuthorizationRequestResolver` at the standard `/oauth2/authorization` base URI, and — when the incoming request carries a `tenantId` query param — copies it into the built `OAuth2AuthorizationRequest`'s `additionalParameters()` map under the key `"tenantId"`. Consumed by `OAuthConfig` (Task 8), read back by `OAuthLoginSuccessHandler` (Task 7).
- Consumes: nothing from earlier tasks.

- [ ] **Step 1: Write the failing test**

```java
package com.example.authsvc.infrastructure.security.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import static org.assertj.core.api.Assertions.assertThat;

class TenantAwareOAuth2AuthorizationRequestResolverTest {

    private static ClientRegistration googleRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .scope("openid", "email")
                .build();
    }

    private TenantAwareOAuth2AuthorizationRequestResolver resolver() {
        ClientRegistrationRepository repo = new InMemoryClientRegistrationRepository(googleRegistration());
        return new TenantAwareOAuth2AuthorizationRequestResolver(repo);
    }

    @Test
    void resolve_withTenantIdParam_addsToAdditionalParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google");
        request.setParameter("tenantId", "11111111-1111-1111-1111-111111111111");

        OAuth2AuthorizationRequest authorizationRequest = resolver().resolve(request);

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getAdditionalParameters())
                .containsEntry("tenantId", "11111111-1111-1111-1111-111111111111");
    }

    @Test
    void resolve_withoutTenantIdParam_noAdditionalParameter() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
        request.setServletPath("/oauth2/authorization/google");

        OAuth2AuthorizationRequest authorizationRequest = resolver().resolve(request);

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getAdditionalParameters()).doesNotContainKey("tenantId");
    }

    @Test
    void resolve_unrelatedPath_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/some/other/path");
        request.setServletPath("/some/other/path");

        assertThat(resolver().resolve(request)).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.security.oauth.TenantAwareOAuth2AuthorizationRequestResolverTest"`
Expected: FAIL (compilation error) — class doesn't exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.authsvc.infrastructure.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Wraps the standard {@link DefaultOAuth2AuthorizationRequestResolver} to
 * carry an optional {@code tenantId} query param (given at
 * {@code /oauth2/authorization/{registrationId}?tenantId=...}) through the
 * authorization-code round trip via {@link OAuth2AuthorizationRequest}'s
 * {@code additionalParameters()} map — this service is multi-tenant and a
 * brand-new account created by {@code OAuthLoginSuccessHandler} (Task 7)
 * needs to know which tenant to assign, the same optional-defaults-to-
 * platform-sentinel behavior {@code RegisterRequest.tenantId} already has.
 */
public class TenantAwareOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String TENANT_ID_PARAM = "tenantId";
    private static final String DEFAULT_AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public TenantAwareOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository, DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return withTenantId(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return withTenantId(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest withTenantId(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request) {
        if (authorizationRequest == null) {
            return null;
        }
        String tenantId = request.getParameter(TENANT_ID_PARAM);
        if (tenantId == null || tenantId.isBlank()) {
            return authorizationRequest;
        }
        return OAuth2AuthorizationRequest.from(authorizationRequest)
                .additionalParameters(params -> params.put(TENANT_ID_PARAM, tenantId))
                .build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.security.oauth.TenantAwareOAuth2AuthorizationRequestResolverTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/oauth/TenantAwareOAuth2AuthorizationRequestResolver.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/oauth/TenantAwareOAuth2AuthorizationRequestResolverTest.java
git commit -m "add TenantAwareOAuth2AuthorizationRequestResolver to carry tenantId through the OAuth round trip"
```

---

### Task 6: `OAuthController` (`/complete-signup`) and its DTOs

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/OAuthCompleteSignupRequest.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/OAuthPasswordSetupRequiredResponse.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/controller/OAuthController.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/api/controller/OAuthControllerTest.java`

**Interfaces:**
- Consumes: `OAuthSignupChallengeStore` (Task 3), `AuthUserJpaRepository` (existing), `PasswordHasher` (existing), `LoginExecutionService.proceedPostAuthentication` (Task 2), `AuthCookieFactory`/`AuthBehaviorProperties` (existing, same as `MfaController`).
- Produces: `POST /api/v1/auth/oauth/complete-signup` — request `OAuthCompleteSignupRequest(String setupToken, String password)`, response either a normal login-shaped body (cookie or JSON per `AuthBehaviorProperties.tokenDeliveryMode`, same as `/login`) or an `MfaLoginChallengeResponse` if the tenant also requires MFA.
- Produces: `OAuthPasswordSetupRequiredResponse(boolean passwordSetupRequired, String setupToken)` — used by `OAuthLoginSuccessHandler` (Task 7), written directly to the HTTP response there (not returned from this controller).

- [ ] **Step 1: Write the failing test**

```java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.OAuthCompleteSignupRequest;
import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MfaChallengeInfo;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.OAuthSignupChallengeEntry;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

    @Mock private OAuthSignupChallengeStore signupChallengeStore;
    @Mock private AuthUserJpaRepository userRepo;
    @Mock private PasswordHasher passwordHasher;
    @Mock private LoginExecutionService loginExecutor;
    @Mock private AuthCookieFactory cookieFactory;
    @Mock private HttpServletRequest httpRequest;

    private AuthBehaviorProperties behaviorProps;
    private OAuthController controller;

    @BeforeEach
    void setUp() {
        behaviorProps = new AuthBehaviorProperties();
        controller = new OAuthController(signupChallengeStore, userRepo, passwordHasher, loginExecutor, cookieFactory, behaviorProps);
    }

    @Test
    void completeSignup_validToken_setsPasswordAndIssuesTokens() {
        UUID userId = UUID.randomUUID();
        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("new-oauth-user@example.com")
                .userType(UserType.TENANT_USER).active(true).build();

        when(signupChallengeStore.find("setup-token")).thenReturn(Optional.of(new OAuthSignupChallengeEntry(userId, 0)));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.hash("NewPassword123!")).thenReturn("hashed-password");

        Instant now = Instant.now();
        LoginResponse response = new LoginResponse(userId, user.getEmail(), now.plusSeconds(900), "access-tok", "refresh-tok");
        LoginResult result = new LoginResult(response, "access-tok", "refresh-tok", Duration.ofMinutes(15), Duration.ofDays(7));
        when(loginExecutor.proceedPostAuthentication(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(result);
        when(cookieFactory.createAccessTokenCookie(anyString(), any())).thenReturn(
                ResponseCookie.from("access_token", "access-tok").build());
        when(cookieFactory.createRefreshTokenCookie(anyString(), any())).thenReturn(
                ResponseCookie.from("refresh_token", "refresh-tok").build());
        when(cookieFactory.clearLegacyRefreshTokenCookie()).thenReturn(ResponseCookie.from("legacy_rt", "").build());
        when(cookieFactory.clearOldNarrowRefreshTokenCookie()).thenReturn(ResponseCookie.from("old_rt", "").build());

        behaviorProps.setTokenDeliveryMode("cookie");
        ResponseEntity<?> httpResponse = controller.completeSignup(
                new OAuthCompleteSignupRequest("setup-token", "NewPassword123!"), httpRequest);

        assertThat(httpResponse.getStatusCode().is2xxSuccessful()).isTrue();
        verify(passwordHasher).hash("NewPassword123!");
        verify(userRepo).save(user);
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        verify(signupChallengeStore).delete("setup-token");
    }

    @Test
    void completeSignup_unknownToken_throwsUnauthorized() {
        when(signupChallengeStore.find("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.completeSignup(
                new OAuthCompleteSignupRequest("bad-token", "NewPassword123!"), httpRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void completeSignup_attemptsExhausted_deletesTokenAndThrowsUnauthorized() {
        UUID userId = UUID.randomUUID();
        when(signupChallengeStore.find("maxed-token")).thenReturn(Optional.of(new OAuthSignupChallengeEntry(userId, 5)));

        assertThatThrownBy(() -> controller.completeSignup(
                new OAuthCompleteSignupRequest("maxed-token", "NewPassword123!"), httpRequest))
                .isInstanceOf(UnauthorizedException.class);

        verify(signupChallengeStore).delete("maxed-token");
    }

    @Test
    void completeSignup_tenantRequiresMfa_returnsChallengeInsteadOfTokens() {
        UUID userId = UUID.randomUUID();
        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("mfa-tenant-user@example.com")
                .userType(UserType.TENANT_USER).active(true).build();

        when(signupChallengeStore.find("setup-token")).thenReturn(Optional.of(new OAuthSignupChallengeEntry(userId, 0)));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.hash(anyString())).thenReturn("hashed-password");
        when(loginExecutor.proceedPostAuthentication(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(LoginResult.challenge(new MfaChallengeInfo("mfa-challenge-token", false)));

        ResponseEntity<?> httpResponse = controller.completeSignup(
                new OAuthCompleteSignupRequest("setup-token", "NewPassword123!"), httpRequest);

        assertThat(httpResponse.getBody()).isInstanceOf(com.example.authsvc.api.dto.response.MfaLoginChallengeResponse.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.api.controller.OAuthControllerTest"`
Expected: FAIL (compilation error) — none of the new classes exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
// OAuthCompleteSignupRequest.java
package com.example.authsvc.api.dto.request;

import com.example.authsvc.common.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record OAuthCompleteSignupRequest(
        @NotBlank(message = "Setup token is required") String setupToken,
        @ValidPassword String password
) {}
```

```java
// OAuthPasswordSetupRequiredResponse.java
package com.example.authsvc.api.dto.response;

public record OAuthPasswordSetupRequiredResponse(
        boolean passwordSetupRequired,
        String setupToken
) {}
```

```java
// OAuthController.java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.OAuthCompleteSignupRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MfaLoginChallengeResponse;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.domain.model.OAuthSignupChallengeEntry;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Completes an OAuth-initiated signup that's been blocked pending a password
 * (see {@code OAuthLoginSuccessHandler}). Only registered when
 * {@code app.oauth.enabled=true}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/oauth")
@ConditionalOnProperty(prefix = "app.oauth", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OAuthController {

    private static final int MAX_SIGNUP_ATTEMPTS = 5;

    private final OAuthSignupChallengeStore signupChallengeStore;
    private final AuthUserJpaRepository     userRepo;
    private final PasswordHasher            passwordHasher;
    private final LoginExecutionService     loginExecutor;
    private final AuthCookieFactory         cookieFactory;
    private final AuthBehaviorProperties    behaviorProps;

    @PostMapping("/complete-signup")
    public ResponseEntity<?> completeSignup(@Valid @RequestBody OAuthCompleteSignupRequest request,
                                             HttpServletRequest httpRequest) {
        OAuthSignupChallengeEntry entry = signupChallengeStore.find(request.setupToken())
                .orElseThrow(UnauthorizedException::new);

        if (entry.attempts() >= MAX_SIGNUP_ATTEMPTS) {
            signupChallengeStore.delete(request.setupToken());
            throw new UnauthorizedException();
        }

        AuthUserEntity user = userRepo.findById(entry.userId()).orElseThrow(UnauthorizedException::new);
        user.setPasswordHash(passwordHasher.hash(request.password()));
        userRepo.save(user);

        signupChallengeStore.delete(request.setupToken());

        String ip = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResult result = loginExecutor.proceedPostAuthentication(user, ip, userAgent, System.currentTimeMillis());

        log.info("oauth.signup.completed userId={}", user.getId());

        // Mirror AuthController.login()/MfaController.verifyLogin()'s
        // challenge-vs-cookie-vs-JSON branching exactly — completing signup
        // ends in a real login, and must honor the same MFA-gate and
        // auth.token-delivery-mode contracts every other login path does.
        if (result.mfaChallenge() != null) {
            return ResponseEntity.ok(new MfaLoginChallengeResponse(
                    true, result.mfaChallenge().challengeToken(), result.mfaChallenge().enrollmentRequired()));
        }

        if (behaviorProps.isJsonTokenDelivery()) {
            return ResponseEntity.ok(result.response());
        }

        ResponseCookie accessCookie = cookieFactory.createAccessTokenCookie(result.accessToken(), result.accessTokenTtl());
        ResponseCookie refreshCookie = cookieFactory.createRefreshTokenCookie(result.refreshToken(), result.refreshTokenTtl());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearOldNarrowRefreshTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.api.controller.OAuthControllerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/OAuthCompleteSignupRequest.java gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/OAuthPasswordSetupRequiredResponse.java gen-auth-starter/src/main/java/com/example/authsvc/api/controller/OAuthController.java gen-auth-starter/src/test/java/com/example/authsvc/api/controller/OAuthControllerTest.java
git commit -m "add OAuthController.completeSignup for the blocking password-setup gate"
```

---

### Task 7: `OAuthLoginSuccessHandler` and `OAuthLoginFailureHandler`

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/oauth/OAuthLoginSuccessHandler.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/oauth/OAuthLoginFailureHandler.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/oauth/OAuthLoginSuccessHandlerTest.java`

**Interfaces:**
- Consumes: `AuthUserJpaRepository`, `AuthUserOAuthIdentityJpaRepository` (Task 1), `OAuthSignupChallengeStore` (Task 3), `LoginExecutionService.proceedPostAuthentication` (Task 2), `AuthCookieFactory`/`AuthBehaviorProperties` (existing), `RedisOAuth2AuthorizationRequestRepository.REQUEST_ATTRIBUTE_NAME` (Task 4).
- Produces: `OAuthLoginSuccessHandler implements AuthenticationSuccessHandler` and `OAuthLoginFailureHandler implements AuthenticationFailureHandler`. Consumed by `OAuthConfig` (Task 8) as beans wired into `SecurityConfig`'s `oauth2Login()`.

This is the account-linking decision from the design spec:
1. `(provider, subject)` identity found → load its user → `proceedPostAuthentication`.
2. No identity, `email` matches an existing `auth_users` row → link identity to it → `proceedPostAuthentication`.
3. No match → create a new user (`passwordHash` left unset/null) + identity row → issue a signup challenge, respond `passwordSetupRequired` instead of tokens.

- [ ] **Step 1: Write the failing test**

```java
package com.example.authsvc.infrastructure.security.oauth;

import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserOAuthIdentityEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserOAuthIdentityJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.application.service.LoginExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthLoginSuccessHandlerTest {

    @Mock private AuthUserJpaRepository userRepo;
    @Mock private AuthUserOAuthIdentityJpaRepository identityRepo;
    @Mock private OAuthSignupChallengeStore signupChallengeStore;
    @Mock private LoginExecutionService loginExecutor;
    @Mock private AuthCookieFactory cookieFactory;

    private AuthBehaviorProperties behaviorProps;
    private OAuthLoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        behaviorProps = new AuthBehaviorProperties();
        behaviorProps.setTokenDeliveryMode("json");
        handler = new OAuthLoginSuccessHandler(userRepo, identityRepo, signupChallengeStore, loginExecutor, cookieFactory, behaviorProps);
    }

    private OidcUser oidcUser(String subject, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("email", email);
        OidcIdToken idToken = new OidcIdToken("id-token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
        return new DefaultOidcUser(List.of(), idToken);
    }

    private OAuth2AuthenticationToken authToken(OidcUser user) {
        return new OAuth2AuthenticationToken(user, Collections.emptyList(), "google");
    }

    @Test
    void existingIdentity_logsInWithoutCreatingAnything() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthUserEntity existing = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("linked@example.com").passwordHash("hash")
                .userType(UserType.TENANT_USER).active(true).build();
        AuthUserOAuthIdentityEntity identity = AuthUserOAuthIdentityEntity.builder()
                .id(UUID.randomUUID()).userId(userId).provider("google")
                .providerSubject("sub-123").email("linked@example.com").build();

        when(identityRepo.findByProviderAndProviderSubject("google", "sub-123")).thenReturn(Optional.of(identity));
        when(userRepo.findById(userId)).thenReturn(Optional.of(existing));

        Instant now = Instant.now();
        LoginResponse response = new LoginResponse(userId, existing.getEmail(), now.plusSeconds(900), "access-tok", "refresh-tok");
        LoginResult result = new LoginResult(response, "access-tok", "refresh-tok", Duration.ofMinutes(15), Duration.ofDays(7));
        when(loginExecutor.proceedPostAuthentication(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(result);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, httpResponse, authToken(oidcUser("sub-123", "linked@example.com")));

        verify(userRepo, org.mockito.Mockito.never()).save(any());
        verify(identityRepo, org.mockito.Mockito.never()).save(any());
        assertThat(httpResponse.getContentAsString()).contains("access-tok");
    }

    @Test
    void noIdentityButEmailMatches_linksToExistingAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthUserEntity existing = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("password-user@example.com").passwordHash("hash")
                .userType(UserType.TENANT_USER).active(true).build();

        when(identityRepo.findByProviderAndProviderSubject("google", "sub-456")).thenReturn(Optional.empty());
        when(userRepo.findByEmailAndActiveTrue("password-user@example.com")).thenReturn(Optional.of(existing));

        Instant now = Instant.now();
        LoginResponse response = new LoginResponse(userId, existing.getEmail(), now.plusSeconds(900), "access-tok", "refresh-tok");
        when(loginExecutor.proceedPostAuthentication(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new LoginResult(response, "access-tok", "refresh-tok", Duration.ofMinutes(15), Duration.ofDays(7)));

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                authToken(oidcUser("sub-456", "password-user@example.com")));

        ArgumentCaptor<AuthUserOAuthIdentityEntity> captor = ArgumentCaptor.forClass(AuthUserOAuthIdentityEntity.class);
        verify(identityRepo).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getProviderSubject()).isEqualTo("sub-456");
        verify(userRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void noMatchAtAll_createsBlockedAccountAndIssuesSignupChallenge() throws Exception {
        when(identityRepo.findByProviderAndProviderSubject("google", "sub-789")).thenReturn(Optional.empty());
        when(userRepo.findByEmailAndActiveTrue("brand-new@example.com")).thenReturn(Optional.empty());
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(signupChallengeStore.issue(any(), any())).thenReturn("setup-token-abc");

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), httpResponse,
                authToken(oidcUser("sub-789", "brand-new@example.com")));

        ArgumentCaptor<AuthUserEntity> userCaptor = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isNull();
        assertThat(userCaptor.getValue().getTenantId()).isEqualTo(TenantConstants.PLATFORM_TENANT_ID);
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("brand-new@example.com");

        verify(identityRepo).save(any());
        verify(loginExecutor, org.mockito.Mockito.never())
                .proceedPostAuthentication(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(httpResponse.getContentAsString()).contains("setup-token-abc").contains("passwordSetupRequired");
    }

    @Test
    void noMatchAtAll_withTenantIdOnRequestAttribute_usesCapturedTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(identityRepo.findByProviderAndProviderSubject("google", "sub-999")).thenReturn(Optional.empty());
        when(userRepo.findByEmailAndActiveTrue("tenant-scoped@example.com")).thenReturn(Optional.empty());
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(signupChallengeStore.issue(any(), any())).thenReturn("setup-token-def");

        MockHttpServletRequest request = new MockHttpServletRequest();
        var capturedAuthRequest = org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("client-id")
                .redirectUri("https://app.example.com/login/oauth2/code/google")
                .scopes(java.util.Set.of("openid"))
                .state("state-1")
                .additionalParameters(Map.of("tenantId", tenantId.toString()))
                .build();
        request.setAttribute(RedisOAuth2AuthorizationRequestRepository.REQUEST_ATTRIBUTE_NAME, capturedAuthRequest);

        handler.onAuthenticationSuccess(request, new MockHttpServletResponse(),
                authToken(oidcUser("sub-999", "tenant-scoped@example.com")));

        ArgumentCaptor<AuthUserEntity> userCaptor = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getTenantId()).isEqualTo(tenantId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.security.oauth.OAuthLoginSuccessHandlerTest"`
Expected: FAIL (compilation error) — none of the new classes exist yet.

- [ ] **Step 3: Write minimal implementation**

```java
// OAuthLoginFailureHandler.java
package com.example.authsvc.infrastructure.security.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

/**
 * Any exchange/validation failure during the OAuth2 login flow (bad state,
 * provider error, invalid ID token) → generic 401 JSON, no reason leaked to
 * the caller. The actual reason is logged server-side only.
 */
@Slf4j
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        log.warn("oauth.login.failed reason={}", exception.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"oauth_login_failed\"}");
    }
}
```

```java
// OAuthLoginSuccessHandler.java
package com.example.authsvc.infrastructure.security.oauth;

import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MfaLoginChallengeResponse;
import com.example.authsvc.api.dto.response.OAuthPasswordSetupRequiredResponse;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserOAuthIdentityEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserOAuthIdentityJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs after Spring Security's {@code oauth2Login()} has already exchanged
 * the authorization code, fetched the ID token, and validated it (issuer,
 * audience, expiry, nonce, signature — all handled by the framework). Applies
 * this service's own account-linking/blocking-gate decision (see the design
 * spec) and issues this service's own JWTs, writing the HTTP response
 * directly instead of delegating to Spring's default redirect behavior.
 */
@Slf4j
@RequiredArgsConstructor
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Duration SIGNUP_CHALLENGE_TTL = Duration.ofMinutes(10);
    private static final String TENANT_ID_PARAM = "tenantId";

    private final AuthUserJpaRepository              userRepo;
    private final AuthUserOAuthIdentityJpaRepository identityRepo;
    private final OAuthSignupChallengeStore          signupChallengeStore;
    private final LoginExecutionService              loginExecutor;
    private final AuthCookieFactory                  cookieFactory;
    private final AuthBehaviorProperties             behaviorProps;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OidcUser oidcUser = (OidcUser) oauthToken.getPrincipal();
        String provider = oauthToken.getAuthorizedClientRegistrationId();
        String subject  = oidcUser.getSubject();
        String email    = oidcUser.getEmail();

        AuthUserEntity user = identityRepo.findByProviderAndProviderSubject(provider, subject)
                .map(identity -> userRepo.findById(identity.getUserId())
                        .orElseThrow(() -> new IllegalStateException(
                                "auth_user_oauth_identity references missing user " + identity.getUserId())))
                .or(() -> linkIfEmailMatches(provider, subject, email))
                .orElse(null);

        String ip        = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        if (user == null) {
            user = createPendingUser(resolveTenantId(request), email);
            saveIdentity(provider, subject, email, user.getId());

            String setupToken = signupChallengeStore.issue(user.getId(), SIGNUP_CHALLENGE_TTL);
            log.info("oauth.signup.pending userId={} provider={}", user.getId(), provider);
            writeJson(response, new OAuthPasswordSetupRequiredResponse(true, setupToken));
            return;
        }

        LoginResult result = loginExecutor.proceedPostAuthentication(user, ip, userAgent, System.currentTimeMillis());
        log.info("oauth.login.completed userId={} provider={}", user.getId(), provider);
        writeLoginResult(response, result);
    }

    private Optional<AuthUserEntity> linkIfEmailMatches(String provider, String subject, String email) {
        return userRepo.findByEmailAndActiveTrue(email).map(existing -> {
            saveIdentity(provider, subject, email, existing.getId());
            return existing;
        });
    }

    private UUID resolveTenantId(HttpServletRequest request) {
        Object attr = request.getAttribute(RedisOAuth2AuthorizationRequestRepository.REQUEST_ATTRIBUTE_NAME);
        if (attr instanceof OAuth2AuthorizationRequest authorizationRequest) {
            Object tenantIdParam = authorizationRequest.getAdditionalParameters().get(TENANT_ID_PARAM);
            if (tenantIdParam instanceof String tenantIdString && !tenantIdString.isBlank()) {
                return UUID.fromString(tenantIdString);
            }
        }
        return TenantConstants.PLATFORM_TENANT_ID;
    }

    private AuthUserEntity createPendingUser(UUID tenantId, String email) {
        AuthUserEntity user = AuthUserEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .email(email)
                .userType(UserType.TENANT_USER)
                .active(true)
                .build();
        return userRepo.save(user);
    }

    private void saveIdentity(String provider, String subject, String email, UUID userId) {
        identityRepo.save(AuthUserOAuthIdentityEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider(provider)
                .providerSubject(subject)
                .email(email)
                .build());
    }

    private void writeLoginResult(HttpServletResponse response, LoginResult result) throws IOException {
        if (result.mfaChallenge() != null) {
            writeJson(response, new MfaLoginChallengeResponse(
                    true, result.mfaChallenge().challengeToken(), result.mfaChallenge().enrollmentRequired()));
            return;
        }
        if (behaviorProps.isJsonTokenDelivery()) {
            writeJson(response, result.response());
            return;
        }
        ResponseCookie accessCookie  = cookieFactory.createAccessTokenCookie(result.accessToken(), result.accessTokenTtl());
        ResponseCookie refreshCookie = cookieFactory.createRefreshTokenCookie(result.refreshToken(), result.refreshTokenTtl());
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearOldNarrowRefreshTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        writeJson(response, result.response());
    }

    private void writeJson(HttpServletResponse response, Object body) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), body);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.security.oauth.OAuthLoginSuccessHandlerTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/oauth/OAuthLoginSuccessHandler.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/oauth/OAuthLoginFailureHandler.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/oauth/OAuthLoginSuccessHandlerTest.java
git commit -m "add OAuthLoginSuccessHandler account-linking/blocking-gate logic and OAuthLoginFailureHandler"
```

---

### Task 8: `OAuthProperties`, `OAuthConfig`, and `SecurityConfig` wiring

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/properties/OAuthProperties.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/OAuthConfig.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/config/OAuthConfigTest.java`

**Interfaces:**
- Produces: `OAuthProperties.enabled` (`boolean`, default `false`) bound from `app.oauth.enabled`. `OAuthConfig` — `@Configuration @ConditionalOnProperty(prefix = "app.oauth", name = "enabled", havingValue = "true")` — registers as `@Bean`s: `OAuthSignupChallengeStore` (Task 3's `RedisOAuthSignupChallengeStore`), `AuthorizationRequestRepository<OAuth2AuthorizationRequest>` (Task 4's `RedisOAuth2AuthorizationRequestRepository`), `OAuth2AuthorizationRequestResolver` (Task 5's `TenantAwareOAuth2AuthorizationRequestResolver`, requires `ClientRegistrationRepository`), `AuthenticationSuccessHandler` (Task 7's `OAuthLoginSuccessHandler`), `AuthenticationFailureHandler` (Task 7's `OAuthLoginFailureHandler`).
- Modifies: `SecurityConfig` — switches from `@RequiredArgsConstructor` to a manual constructor (same reason `LoginExecutionServiceImpl` already does this: `@Autowired(required = false)` needs constructor-parameter placement, which Lombok's `@RequiredArgsConstructor` can't express) so `ClientRegistrationRepository`/the four OAuth beans above are optional collaborators, present only when `app.oauth.enabled=true` **and** at least one provider is configured. `filterChain(...)` adds the OAuth2 permitAll paths and conditionally attaches `.oauth2Login(...)`.

- [ ] **Step 1: Write the failing test**

```java
package com.example.authsvc.config;

import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserOAuthIdentityJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OAuthConfigTest {

    private static ClientRegistration googleRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .scope("openid", "email")
                .build();
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class))
            .withBean(AuthUserJpaRepository.class, () -> mock(AuthUserJpaRepository.class))
            .withBean(AuthUserOAuthIdentityJpaRepository.class, () -> mock(AuthUserOAuthIdentityJpaRepository.class))
            .withBean(LoginExecutionService.class, () -> mock(LoginExecutionService.class))
            .withBean(AuthCookieFactory.class, () -> mock(AuthCookieFactory.class))
            .withBean(AuthBehaviorProperties.class, AuthBehaviorProperties::new)
            .withUserConfiguration(OAuthConfig.class);

    @Test
    void oauthDisabled_noOAuthBeansRegistered() {
        contextRunner
                .withPropertyValues("app.oauth.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OAuthSignupChallengeStore.class);
                    assertThat(context).doesNotHaveBean(AuthorizationRequestRepository.class);
                    assertThat(context).doesNotHaveBean(AuthenticationSuccessHandler.class);
                });
    }

    @Test
    void oauthEnabled_allBeansPresent() {
        contextRunner
                .withBean(ClientRegistrationRepository.class,
                        () -> new InMemoryClientRegistrationRepository(googleRegistration()))
                .withPropertyValues("app.oauth.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OAuthSignupChallengeStore.class);
                    assertThat(context).hasSingleBean(AuthorizationRequestRepository.class);
                    assertThat(context).hasSingleBean(OAuth2AuthorizationRequestResolver.class);
                    assertThat(context).hasSingleBean(AuthenticationSuccessHandler.class);
                    assertThat(context).hasSingleBean(AuthenticationFailureHandler.class);
                });
    }

    @Test
    void oauthEnabled_noProviderConfigured_failsFast() {
        contextRunner
                .withPropertyValues("app.oauth.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.config.OAuthConfigTest"`
Expected: FAIL (compilation error) — `OAuthConfig` doesn't exist yet.

- [ ] **Step 3: Write `OAuthProperties` and `OAuthConfig`**

```java
// OAuthProperties.java
package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gates the OAuth login subsystem, bound from {@code app.oauth.*}. Provider
 * identity (issuer/client-id/client-secret/scopes) is configured entirely
 * through Spring Boot's own {@code spring.security.oauth2.client.*}
 * properties, not duplicated here.
 */
@Data
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    /** Off by default — no beans registered, no oauth2Login() attached. */
    private boolean enabled = false;
}
```

```java
// OAuthConfig.java
package com.example.authsvc.config;

import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.cache.RedisOAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserOAuthIdentityJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.oauth.OAuthLoginFailureHandler;
import com.example.authsvc.infrastructure.security.oauth.OAuthLoginSuccessHandler;
import com.example.authsvc.infrastructure.security.oauth.RedisOAuth2AuthorizationRequestRepository;
import com.example.authsvc.infrastructure.security.oauth.TenantAwareOAuth2AuthorizationRequestResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.jackson2.OAuth2ClientJackson2Module;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Registers the OAuth login collaborators that have no {@code @Component} of
 * their own. Only active when {@code app.oauth.enabled=true}. Note: enabling
 * this without also configuring at least one
 * {@code spring.security.oauth2.client.registration.<id>.*} entry means
 * {@link ClientRegistrationRepository} is never registered by Spring Boot,
 * so {@link #oAuth2AuthorizationRequestResolver} fails to construct and the
 * application fails fast at startup — this is intentional, not a bug.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.oauth", name = "enabled", havingValue = "true")
public class OAuthConfig {

    @Bean
    public OAuthSignupChallengeStore oAuthSignupChallengeStore(StringRedisTemplate redisTemplate) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new RedisOAuthSignupChallengeStore(redisTemplate, mapper);
    }

    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> oAuth2AuthorizationRequestRepository(
            StringRedisTemplate redisTemplate) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new OAuth2ClientJackson2Module());
        return new RedisOAuth2AuthorizationRequestRepository(redisTemplate, mapper);
    }

    @Bean
    public OAuth2AuthorizationRequestResolver oAuth2AuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        return new TenantAwareOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
    }

    @Bean
    public AuthenticationSuccessHandler oAuthLoginSuccessHandler(
            AuthUserJpaRepository userRepo,
            AuthUserOAuthIdentityJpaRepository identityRepo,
            OAuthSignupChallengeStore signupChallengeStore,
            LoginExecutionService loginExecutor,
            AuthCookieFactory cookieFactory,
            AuthBehaviorProperties behaviorProps) {
        return new OAuthLoginSuccessHandler(
                userRepo, identityRepo, signupChallengeStore, loginExecutor, cookieFactory, behaviorProps);
    }

    @Bean
    public AuthenticationFailureHandler oAuthLoginFailureHandler() {
        return new OAuthLoginFailureHandler();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.config.OAuthConfigTest"`
Expected: PASS

- [ ] **Step 5: Wire into `SecurityConfig`**

Change the class from `@RequiredArgsConstructor` with `final` fields to a manual constructor. Replace:

```java
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter  jwtFilter;
    private final InternalTokenAuthFilter  internalTokenAuthFilter;
    private final JwtAuthEntryPoint        authEntryPoint;
    private final JwtAccessDeniedHandler   accessDeniedHandler;
    private final CorsProperties           corsProperties;
```

with:

```java
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter  jwtFilter;
    private final InternalTokenAuthFilter  internalTokenAuthFilter;
    private final JwtAuthEntryPoint        authEntryPoint;
    private final JwtAccessDeniedHandler   accessDeniedHandler;
    private final CorsProperties           corsProperties;

    /** Non-null only when app.oauth.enabled=true AND a provider is configured — see OAuthConfig. */
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizationRequestResolver oAuth2AuthorizationRequestResolver;
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> oAuth2AuthorizationRequestRepository;
    private final AuthenticationSuccessHandler oAuthLoginSuccessHandler;
    private final AuthenticationFailureHandler oAuthLoginFailureHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtFilter,
            InternalTokenAuthFilter internalTokenAuthFilter,
            JwtAuthEntryPoint authEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler,
            CorsProperties corsProperties,
            @Autowired(required = false) ClientRegistrationRepository clientRegistrationRepository,
            @Autowired(required = false) OAuth2AuthorizationRequestResolver oAuth2AuthorizationRequestResolver,
            @Autowired(required = false) AuthorizationRequestRepository<OAuth2AuthorizationRequest> oAuth2AuthorizationRequestRepository,
            @Autowired(required = false) AuthenticationSuccessHandler oAuthLoginSuccessHandler,
            @Autowired(required = false) AuthenticationFailureHandler oAuthLoginFailureHandler) {
        this.jwtFilter = jwtFilter;
        this.internalTokenAuthFilter = internalTokenAuthFilter;
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsProperties = corsProperties;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.oAuth2AuthorizationRequestResolver = oAuth2AuthorizationRequestResolver;
        this.oAuth2AuthorizationRequestRepository = oAuth2AuthorizationRequestRepository;
        this.oAuthLoginSuccessHandler = oAuthLoginSuccessHandler;
        this.oAuthLoginFailureHandler = oAuthLoginFailureHandler;
    }
```

Add these imports:

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
```

In `filterChain(...)`, add `"/api/v1/auth/oauth/complete-signup"` to the existing `POST` permitAll list (same category as `/api/v1/auth/mfa/verify-login` — setup-token-authenticated, not JWT-authenticated), add a new `GET` permitAll entry for Spring's own OAuth2 endpoints, and change the single-expression `SecurityFilterChain chain = http....build();` into a multi-statement method so the `oauth2Login()` block can be attached conditionally:

```java
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ── Truly public auth endpoints — no JWT needed ──────────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/register",
                                "/api/v1/auth/magic-link/**",
                                "/api/v1/auth/super-admin/**",
                                "/api/v1/auth/mfa/verify-login",
                                "/api/v1/auth/mfa/enroll",
                                // Setup-token-authenticated, not JWT-authenticated — the
                                // caller has no session yet (a brand-new OAuth signup is
                                // blocked pending a password, same category as the MFA
                                // challenge-token endpoints above).
                                "/api/v1/auth/oauth/complete-signup"
                        ).permitAll()
                        // ── Spring Security's own OAuth2 login endpoints ──────────────
                        .requestMatchers(HttpMethod.GET,
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**"
                        ).permitAll()
                        // ── Internal service-to-service endpoints — secret-header protected ──
                        .requestMatchers("/internal/**").permitAll()
                        // ── JWKS, actuator, docs — always public ─────────────────────
                        .requestMatchers(
                                "/.well-known/jwks.json",
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/v1/docs", "/v1/docs/**",
                                "/swagger-ui/**",
                                "/v1/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**"
                        ).permitAll()
                        // ── Protected auth endpoints — JWT required ──────────────────
                        .requestMatchers(HttpMethod.GET,  "/api/v1/auth/session").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(internalTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter,               UsernamePasswordAuthenticationFilter.class);

        if (clientRegistrationRepository != null) {
            http.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(a -> a
                            .authorizationRequestResolver(oAuth2AuthorizationRequestResolver)
                            .authorizationRequestRepository(oAuth2AuthorizationRequestRepository))
                    .successHandler(oAuthLoginSuccessHandler)
                    .failureHandler(oAuthLoginFailureHandler));
            log.info("oauth2.login.enabled");
        }

        SecurityFilterChain chain = http.build();
        log.info("security.initialized stateless=true cors_origins={}", corsProperties.getAllowedOrigins());
        return chain;
    }
```

- [ ] **Step 6: Compile and run the full security-adjacent test suite**

Run: `./gradlew :gen-auth-starter:compileJava :gen-auth-starter:test --tests "com.example.authsvc.config.OAuthConfigTest" --tests "com.example.authsvc.infrastructure.security.oauth.*"`
Expected: BUILD SUCCESSFUL, all PASS

- [ ] **Step 7: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/config/properties/OAuthProperties.java gen-auth-starter/src/main/java/com/example/authsvc/config/OAuthConfig.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java gen-auth-starter/src/test/java/com/example/authsvc/config/OAuthConfigTest.java
git commit -m "wire OAuth2 login into SecurityConfig behind app.oauth.enabled"
```

---

### Task 9: Demo app config, integration manual, manual E2E verification

**Files:**
- Modify: `gen-auth-demo/src/main/resources/application.yaml` (add `app.oauth` block + example `spring.security.oauth2.client.*` Google config, same style as the existing `app.mfa`/`app.messaging` blocks)
- Modify: `docs/gen-auth-starter-integration-manual.html` (update the scope grid / lede that currently reads "OAuth / social login (not started, no ETA)")

**Interfaces:** none — documentation and example-config only.

- [ ] **Step 1: Add demo config**

In `gen-auth-demo/src/main/resources/application.yaml`, after the existing `mfa:` block under `app:` (around line 182), add:

```yaml
  # ===================================================================
  # OAUTH LOGIN (optional — off by default)
  # ===================================================================
  # To try this locally: create a Google OAuth 2.0 Client ID (Google Cloud
  # Console > APIs & Services > Credentials), add
  # http://localhost:8080/login/oauth2/code/google as an authorized redirect
  # URI, set OAUTH_GOOGLE_CLIENT_ID/OAUTH_GOOGLE_CLIENT_SECRET, flip the flag
  # to true, then visit http://localhost:8080/oauth2/authorization/google.
  oauth:
    enabled: false
```

And after the `mfa:` top-level block (around line 224, the `mfa.secret-encryption-key` section), add a new top-level section:

```yaml
# ===================================================================
# OAUTH2 CLIENT CONFIG (only used when app.oauth.enabled=true)
# ===================================================================

spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${OAUTH_GOOGLE_CLIENT_ID:}
            client-secret: ${OAUTH_GOOGLE_CLIENT_SECRET:}
            scope:
              - openid
              - email
              - profile
        provider:
          google:
            issuer-uri: https://accounts.google.com
```

(If a top-level `spring:` key already exists elsewhere in this file, merge `security.oauth2.client` under the existing key instead of adding a second `spring:` root key — YAML does not allow duplicate top-level keys.)

- [ ] **Step 2: Update the integration manual**

In `docs/gen-auth-starter-integration-manual.html`, find the line (around line 361):

```html
            <li>OAuth / social login (not started, no ETA)</li>
```

Replace with:

```html
            <li>OAuth / social login &mdash; done (Spring Security OAuth2 Client, any OIDC provider)</li>
```

Find the paragraph (around line 799) that reads:

```html
      Updated after the MFA slice merged to <code class="inline">main</code> (2026-07-17) &mdash; the last item on the original punch list before this is <strong>OAuth / social login</strong>, not yet started. Source of truth for anything not covered here: ...
```

Replace with:

```html
      Updated after the OAuth slice merged to <code class="inline">main</code> (2026-08-25) &mdash; every item on the original punch list is now built. Source of truth for anything not covered here: <code class="inline">docs/superpowers/specs/2026-07-16-starter-library-design.md</code> (core module), <code class="inline">docs/superpowers/specs/2026-07-16-restore-deferred-features-design.md</code> (email/magic-link, super-admin, messaging), <code class="inline">docs/superpowers/specs/2026-07-15-mfa-design.md</code> (MFA), <code class="inline">docs/superpowers/specs/2026-08-25-oauth-design.md</code> (OAuth), and the starter's own README in the Gen_AUTH repository.
```

Add a new chapter-06-style subsection (following the existing MFA subsection's format in the same chapter) documenting: `app.oauth.enabled` flag, required `spring.security.oauth2.client.registration.<id>.*`/`.provider.<id>.issuer-uri` properties, the three endpoints from the design spec's endpoint table, and the `passwordSetupRequired`/`complete-signup` flow for brand-new accounts.

- [ ] **Step 3: Run the full test suite**

Run: `./gradlew :gen-auth-starter:test`
Expected: BUILD SUCCESSFUL, all tests pass (existing suite plus every test added in Tasks 1-8)

- [ ] **Step 4: Manual E2E verification against a real Google OAuth sandbox app**

Not automatable — requires a real Google Cloud OAuth 2.0 Client ID and a running Postgres+Redis (`docker compose up -d postgres redis`), per this repo's existing manual-verification convention (see the MFA slice's commit `03fb40f fix three real bugs found during MFA manual end-to-end verification`). Verify each of these scenarios and note any bugs found (fix them in follow-up commits, same as the MFA slice did):

1. Fresh signup: visit `/oauth2/authorization/google`, log in with a Google account never seen by this app before → land on `passwordSetupRequired` with a `setupToken`, no real tokens issued yet.
2. `POST /api/v1/auth/oauth/complete-signup` with that token + a valid password → real tokens issued, `auth_users.password_hash` now set.
3. Log out, repeat step 1 with the same Google account → identity found this time, logs straight in with real tokens (no second signup gate).
4. Existing-email auto-link: register a normal password account with email X, then OAuth-login with a Google account whose email is also X → auto-links, logs in with real tokens, no duplicate `auth_users` row.
5. Tenant with `tenant_settings.mfa_required=true` (set via the existing internal endpoint): repeat both the OAuth-login-of-existing-user path and the complete-signup path → both must return an MFA challenge instead of real tokens.
6. Restart the app with `app.oauth.enabled=true` and no `spring.security.oauth2.client.registration.*` configured → confirm it fails fast at startup with a clear error, not a silent 404 on the OAuth endpoints.

- [ ] **Step 5: Commit**

```bash
git add gen-auth-demo/src/main/resources/application.yaml docs/gen-auth-starter-integration-manual.html
git commit -m "add example app.oauth config to demo app; update integration manual for OAuth"
```
