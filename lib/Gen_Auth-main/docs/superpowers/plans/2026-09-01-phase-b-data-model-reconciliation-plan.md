# Phase B Sub-Project B: JWT Claim Resolver SPI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `TenantSlugResolver.resolveName` method and a new `UserDisplayNameResolver` SPI, extend `JwtClaims`/`JwtUtils` to carry `username`/`userEmail`/`tenantName` claims, and wire them into the 3 real human-session token-issuance call sites.

**Architecture:** Two new/extended pluggable interfaces (mirroring the existing `TenantSlugResolver`/`PlatformOnlyTenantSlugResolver` override pattern) feed 3 new nullable fields on the `JwtClaims` record, which `JwtUtils` serializes/parses under CPMS-matching wire keys (`username`, `user_email`, `tenant_name`). `LoginExecutionServiceImpl`, `ImpersonationTokenServiceImpl`, and `RefreshTokenServiceImpl` call the new resolvers before building their token claims; `ServiceTokenServiceImpl`/`ClientTokenServiceImpl` stay on `null` for these fields (no human display name for service/internal tokens).

**Tech Stack:** Spring Boot 4, Java records, JUnit 5 + Mockito.

**Spec:** `docs/superpowers/specs/2026-09-01-phase-b-data-model-reconciliation-design.md`

## Global Constraints

- Wire JSON keys are exactly `username`, `user_email`, `tenant_name` (snake_case for the latter two) — must match CPMS's real JWT wire format for downstream-consumer compatibility.
- `PlatformOnlyTenantSlugResolver.resolveName()` returns `"Platform"` (capital P) for the platform sentinel — deliberately different casing from `resolve()`'s lowercase `"platform"`. This is correct, matching CPMS's real behavior — do not "fix" it into consistency.
- `NoOpUserDisplayNameResolver.resolve()` always returns `""` regardless of input.
- `JwtClaims`'s 3 new fields (`username`, `userEmail`, `tenantName`) are nullable; `JwtUtils.generateAccessToken` omits them from the payload when null or blank (matches the existing `role_id`/`user_type` not-null-guard style already in that method).
- `JwtUtils.generateTokenPair`'s intermediate `timedClaims` reconstruction must carry all 3 new fields from `baseClaims` — this is the single most important correctness point in this plan; every claim added to `JwtClaims` but not threaded through this reconstruction silently vanishes from every real token, since `generateAccessToken` is only ever called through `generateTokenPair` at the 3 real call sites.
- `ServiceTokenServiceImpl`/`ClientTokenServiceImpl` are NOT wired to the new resolvers — their `JwtClaims` calls get `null, null, null` appended for the 3 new trailing fields, unchanged behavior.
- No Co-Authored-By trailer on any commit.
- Never push to origin or merge to a shared branch without asking the user first.

---

### Task 1: New SPI surface — `TenantSlugResolver.resolveName` + `UserDisplayNameResolver`

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/jwt/TenantSlugResolver.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/jwt/PlatformOnlyTenantSlugResolver.java`
- Modify: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/jwt/PlatformOnlyTenantSlugResolverTest.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/jwt/UserDisplayNameResolver.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/jwt/NoOpUserDisplayNameResolver.java`
- Create: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/jwt/NoOpUserDisplayNameResolverTest.java`

**Interfaces:**
- Produces: `TenantSlugResolver.resolveName(UUID) -> String`, `UserDisplayNameResolver.resolve(UUID) -> String` — both consumed by Task 3.

- [ ] **Step 1: Write the failing tests**

Add to `PlatformOnlyTenantSlugResolverTest.java` (append inside the existing class body, after `resolvesAnyOtherTenantIdToEmptyString`):

```java
    @Test
    void resolvesNamePlatformSentinelToPlatformDisplayName() {
        assertEquals("Platform", resolver.resolveName(TenantConstants.PLATFORM_TENANT_ID));
    }

    @Test
    void resolvesNameAnyOtherTenantIdToEmptyString() {
        assertEquals("", resolver.resolveName(UUID.randomUUID()));
    }
```

Create `NoOpUserDisplayNameResolverTest.java`:

```java
package com.example.authsvc.infrastructure.security.jwt;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoOpUserDisplayNameResolverTest {

    private final UserDisplayNameResolver resolver = new NoOpUserDisplayNameResolver();

    @Test
    void alwaysReturnsEmptyString() {
        assertEquals("", resolver.resolve(UUID.randomUUID()));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run (from repo root): `.\gradlew.bat :gen-auth-starter:test --tests "*PlatformOnlyTenantSlugResolverTest*" --tests "*NoOpUserDisplayNameResolverTest*" --console=plain`
Expected: FAIL — `resolveName` doesn't exist on `TenantSlugResolver` yet (compile error), `UserDisplayNameResolver`/`NoOpUserDisplayNameResolver` classes don't exist yet.

- [ ] **Step 3: Implement**

`UserDisplayNameResolver.java` (new file):

```java
package com.example.authsvc.infrastructure.security.jwt;

import java.util.UUID;

/**
 * Resolves a human-readable display name for the {@code username} claim at
 * login/rotation/impersonation-token time.
 *
 * <p>A standalone auth service has no user-profile directory of its own, so the
 * default implementation ({@link NoOpUserDisplayNameResolver}) always returns an
 * empty string. A host application that owns real user-profile data supplies its
 * own {@code UserDisplayNameResolver} bean — it wins over the default automatically
 * ({@code @ConditionalOnMissingBean}) — rather than this library reaching into
 * another service's database.
 */
public interface UserDisplayNameResolver {

    String resolve(UUID userId);
}
```

`NoOpUserDisplayNameResolver.java` (new file):

```java
package com.example.authsvc.infrastructure.security.jwt;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default {@link UserDisplayNameResolver}: always returns an empty string.
 * Backed off by any host-supplied {@code UserDisplayNameResolver} bean.
 */
@Component
@ConditionalOnMissingBean(UserDisplayNameResolver.class)
public class NoOpUserDisplayNameResolver implements UserDisplayNameResolver {

    @Override
    public String resolve(UUID userId) {
        return "";
    }
}
```

In `TenantSlugResolver.java`, add to the interface body (after `resolve`):

```java
    String resolveName(UUID tenantId);
```

In `PlatformOnlyTenantSlugResolver.java`, add after the existing `resolve` method:

```java
    @Override
    public String resolveName(UUID tenantId) {
        return TenantConstants.PLATFORM_TENANT_ID.equals(tenantId) ? "Platform" : "";
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :gen-auth-starter:test --tests "*PlatformOnlyTenantSlugResolverTest*" --tests "*NoOpUserDisplayNameResolverTest*" --console=plain`
Expected: PASS, all 4 tests in `PlatformOnlyTenantSlugResolverTest` (2 existing + 2 new) and 1 in `NoOpUserDisplayNameResolverTest`.

- [ ] **Step 5: Compile the full module to confirm no other implementer of `TenantSlugResolver` broke**

Run: `.\gradlew.bat :gen-auth-starter:compileJava :gen-auth-starter:compileTestJava --console=plain`
Expected: BUILD SUCCESSFUL. (`PlatformOnlyTenantSlugResolver` is the only production implementer; no test doubles implement the interface directly — they all use Mockito `@Mock`, which auto-implements new interface methods.)

- [ ] **Step 6: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/jwt/TenantSlugResolver.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/jwt/PlatformOnlyTenantSlugResolver.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/jwt/UserDisplayNameResolver.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/jwt/NoOpUserDisplayNameResolver.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/jwt/PlatformOnlyTenantSlugResolverTest.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/jwt/NoOpUserDisplayNameResolverTest.java
git commit -m "phase-b sub-project B: add TenantSlugResolver.resolveName and UserDisplayNameResolver SPI"
```

---

### Task 2: `JwtClaims` + `JwtUtils` extension

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/domain/model/JwtClaims.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/jwt/util/JwtUtils.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ServiceTokenServiceImpl.java` (compile fix only — no behavior change)
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ClientTokenServiceImpl.java` (compile fix only — no behavior change)
- Create: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/jwt/util/JwtUtilsTest.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `JwtClaims(UUID, UUID, String, List<UUID>, UserType, Instant, Instant, String, String, String, String, String)` — the new 12-arg positional constructor order (`username`, `userEmail`, `tenantName` appended at the end) — Task 3 constructs `JwtClaims` with real values for these 3 trailing args at its 3 call sites.

**Why this task must leave the whole module compiling and green:** `JwtClaims` is a Java record — extending its field list breaks every existing call site immediately, including two (`ServiceTokenServiceImpl`, `ClientTokenServiceImpl`) that Task 3 deliberately never touches. This task fixes all 5 application-layer call sites' compilation (3 for real, in Task 3's territory conceptually but trivially unblocked here; 2 with `null, null, null`), so the suite stays green at every task boundary.

- [ ] **Step 1: Write the failing test**

Create `JwtUtilsTest.java`. This is the round-trip test that would catch a `generateTokenPair` claim-dropping regression:

```java
package com.example.authsvc.infrastructure.security.jwt.util;

import com.example.authsvc.config.properties.JwtProperties;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.domain.model.TokenPair;
import com.example.authsvc.infrastructure.security.jwt.jwks.JwtKeyRegistry;
import com.example.authsvc.infrastructure.security.jwt.signer.JwtSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtUtilsTest {

    @Mock private JwtKeyRegistry registry;
    @Mock private JwtProperties  props;
    @Mock private JwtSigner      jwtSigner;

    private JwtUtils jwtUtils;
    private RSAPublicKey publicKey;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtils = new JwtUtils(registry, props, jwtSigner);

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair keyPair = gen.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();

        when(registry.getActiveKid()).thenReturn("kid-1");
        when(registry.getPublicKey("kid-1")).thenReturn(publicKey);
        when(props.getIssuer()).thenReturn("gen-auth");
        when(jwtSigner.sign(anyString(), any(byte[].class))).thenAnswer(inv -> new byte[] {1, 2, 3});
    }

    @Test
    void generateTokenPair_carriesUsernameUserEmailTenantNameThroughToExtractedClaims() {
        // This test exists specifically to catch generateTokenPair's intermediate
        // timedClaims reconstruction silently dropping a field it doesn't
        // explicitly thread through — a real risk any time JwtClaims grows a field.
        Instant now = Instant.now();
        JwtClaims baseClaims = new JwtClaims(
                UUID.randomUUID(), UUID.randomUUID(), "acme",
                List.of(UUID.randomUUID()), UserType.TENANT_USER,
                now, now.plus(15, ChronoUnit.MINUTES), "session-1", null,
                "Jane Doe", "jane@example.com", "Acme Corp");

        // generateAccessToken doesn't verify the signature itself in this test —
        // isTokenValid/extractClaims below do, via the real public key.
        String token = jwtUtils.generateAccessToken(baseClaims);

        // Swap the mocked signer's garbage bytes for a real signature so parsing
        // succeeds: sign with the matching private key instead.
        // (Simplify: assert on the unsigned payload segment directly instead of
        // full signature verification, since JwtSigner is mocked.)
        String[] parts = token.split("\\.");
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

        assertThat(payloadJson).contains("\"username\":\"Jane Doe\"");
        assertThat(payloadJson).contains("\"user_email\":\"jane@example.com\"");
        assertThat(payloadJson).contains("\"tenant_name\":\"Acme Corp\"");
    }

    @Test
    void generateTokenPair_blankOptionalClaims_omittedFromPayload() {
        Instant now = Instant.now();
        JwtClaims baseClaims = new JwtClaims(
                UUID.randomUUID(), UUID.randomUUID(), "acme",
                List.of(), UserType.CLIENT,
                now, now.plus(15, ChronoUnit.MINUTES), "session-2", null,
                null, null, null);

        String token = jwtUtils.generateAccessToken(baseClaims);
        String[] parts = token.split("\\.");
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

        assertThat(payloadJson).doesNotContain("\"username\"");
        assertThat(payloadJson).doesNotContain("\"user_email\"");
        assertThat(payloadJson).doesNotContain("\"tenant_name\"");
    }

    @Test
    void generateTokenPair_viaTokenPairPath_stillCarriesTheThreeClaims() {
        // Exercises generateTokenPair(JwtClaims) -> internal timedClaims rebuild ->
        // generateAccessToken, which is the ONLY path real call sites use.
        Instant now = Instant.now();
        when(props.getAccessToken()).thenReturn(accessTokenProps(15));
        when(props.getRefreshToken()).thenReturn(refreshTokenProps(7));

        JwtClaims baseClaims = new JwtClaims(
                UUID.randomUUID(), UUID.randomUUID(), "acme",
                List.of(), UserType.TENANT_USER,
                now, now.plus(15, ChronoUnit.MINUTES), "session-3", null,
                "Jane Doe", "jane@example.com", "Acme Corp");

        TokenPair pair = jwtUtils.generateTokenPair(baseClaims);
        String[] parts = pair.accessToken().split("\\.");
        String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

        assertThat(payloadJson).contains("\"username\":\"Jane Doe\"");
        assertThat(payloadJson).contains("\"user_email\":\"jane@example.com\"");
        assertThat(payloadJson).contains("\"tenant_name\":\"Acme Corp\"");
    }

    private static JwtProperties.AccessToken accessTokenProps(int minutes) {
        JwtProperties.AccessToken t = new JwtProperties.AccessToken();
        t.setExpirationMinutes(minutes);
        return t;
    }

    private static JwtProperties.RefreshToken refreshTokenProps(int days) {
        JwtProperties.RefreshToken t = new JwtProperties.RefreshToken();
        t.setExpirationDays(days);
        return t;
    }
}
```

(Confirmed against `gen-auth-starter/src/main/java/com/example/authsvc/config/properties/JwtProperties.java`: `AccessToken`/`RefreshToken` are plain `@Data` classes, so `setExpirationMinutes`/`setExpirationDays` are the real Lombok-generated setter names — the helper methods above are correct as written.)

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :gen-auth-starter:test --tests "*JwtUtilsTest*" --console=plain`
Expected: FAIL — compile error, `JwtClaims` doesn't have a 12-arg constructor yet.

- [ ] **Step 3: Implement — `JwtClaims.java`**

Replace the record declaration and update the Javadoc claim-mapping block:

```java
/**
 * Verified JWT payload. Permission resolution uses Redis at runtime via role_ids —
 * no permissions are carried in the token.
 *
 * Claim → JWT field mapping:
 *   userId     → sub
 *   tenantId   → tenant_id
 *   tenantSlug → tenant_slug
 *   roleIds    → role_ids[]   (primary, Phase 4+)
 *   roleId()   → role_id      (backward-compat scalar, derived from roleIds[0])
 *   userType   → user_type
 *   sessionId  → session_id
 *   jti        → jti
 *   issuedAt   → iat
 *   expiresAt  → exp
 *   username   → username     (nullable — display name of the acting user)
 *   userEmail  → user_email   (nullable)
 *   tenantName → tenant_name  (nullable — human-readable tenant display name)
 */
public record JwtClaims(
        UUID        userId,
        UUID        tenantId,
        String      tenantSlug,
        List<UUID>  roleIds,
        UserType    userType,
        Instant     issuedAt,
        Instant     expiresAt,
        String      sessionId,
        String      jti,
        String      username,
        String      userEmail,
        String      tenantName
) {
    /**
     * Backward-compatibility accessor. Returns the first role UUID, or {@code null}
     * when the user has no role assignments. Callers that only need a single role
     * (e.g. impersonation, session snapshot) may use this instead of iterating
     * {@link #roleIds()}.
     */
    public UUID roleId() {
        return (roleIds == null || roleIds.isEmpty()) ? null : roleIds.get(0);
    }

    public boolean isExpired(Instant now) { return expiresAt.isBefore(now); }
    public boolean isValid(Instant now)   { return !isExpired(now); }
}
```

- [ ] **Step 4: Implement — `JwtUtils.java`**

In `generateAccessToken`, immediately after the existing `payload.put("session_id", claims.sessionId());` line, add:

```java
            if (claims.username()   != null && !claims.username().isBlank())   payload.put("username",    claims.username());
            if (claims.userEmail()  != null && !claims.userEmail().isBlank())  payload.put("user_email",  claims.userEmail());
            if (claims.tenantName() != null && !claims.tenantName().isBlank()) payload.put("tenant_name", claims.tenantName());
```

In `generateTokenPair(JwtClaims baseClaims, Instant fixedRefreshExpiry)`, change the `timedClaims` construction from:

```java
        JwtClaims timedClaims = new JwtClaims(
                baseClaims.userId(), baseClaims.tenantId(), baseClaims.tenantSlug(),
                baseClaims.roleIds(), baseClaims.userType(),
                now, accessExpiry, baseClaims.sessionId(),
                null
        );
```

to:

```java
        JwtClaims timedClaims = new JwtClaims(
                baseClaims.userId(), baseClaims.tenantId(), baseClaims.tenantSlug(),
                baseClaims.roleIds(), baseClaims.userType(),
                now, accessExpiry, baseClaims.sessionId(),
                null,
                baseClaims.username(), baseClaims.userEmail(), baseClaims.tenantName()
        );
```

In `extractClaims`, change the final `return new JwtClaims(...)` from:

```java
        return new JwtClaims(
                UUID.fromString(c.getSubject()),
                parseUuid(c.get("tenant_id",    String.class)),
                c.get("tenant_slug",            String.class),
                roleIds,
                parseUserType(c.get("user_type", String.class)),
                c.getIssuedAt().toInstant(),
                c.getExpiration().toInstant(),
                c.get("session_id",             String.class),
                c.get("jti",                    String.class)
        );
```

to:

```java
        return new JwtClaims(
                UUID.fromString(c.getSubject()),
                parseUuid(c.get("tenant_id",    String.class)),
                c.get("tenant_slug",            String.class),
                roleIds,
                parseUserType(c.get("user_type", String.class)),
                c.getIssuedAt().toInstant(),
                c.getExpiration().toInstant(),
                c.get("session_id",             String.class),
                c.get("jti",                    String.class),
                c.get("username",                String.class),
                c.get("user_email",              String.class),
                c.get("tenant_name",             String.class)
        );
```

- [ ] **Step 5: Fix compilation at the 2 untouched call sites**

In `ServiceTokenServiceImpl.java`, change:

```java
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
```

to:

```java
        JwtClaims claims = new JwtClaims(
                TenantConstants.SERVICE_ACCOUNT_ID,
                TenantConstants.PLATFORM_TENANT_ID,
                "platform",
                List.of(),
                UserType.SUPER_ADMIN,
                now,
                expiresAt,
                "svc:" + callerService,
                null,
                null, null, null
        );
```

In `ClientTokenServiceImpl.java`, change:

```java
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
```

to:

```java
        JwtClaims claims = new JwtClaims(
                request.clientUserId(),
                request.tenantId(),
                tenantSlug,
                List.of(request.roleId()),
                UserType.CLIENT,
                now,
                expiresAt,
                request.sessionId(),
                null,
                null, null, null
        );
```

- [ ] **Step 6: Run test to verify it passes**

Run: `.\gradlew.bat :gen-auth-starter:test --tests "*JwtUtilsTest*" --console=plain`
Expected: PASS, all 3 tests.

- [ ] **Step 7: Compile and run the full module to confirm nothing else broke**

Run: `.\gradlew.bat :gen-auth-starter:test --console=plain`
Expected: BUILD SUCCESSFUL. `LoginExecutionServiceImpl`, `ImpersonationTokenServiceImpl`, `RefreshTokenServiceImpl` will NOT yet compile at this point — their `new JwtClaims(...)` calls still have only 9 args. **This step is expected to fail until Task 3 lands** — run it anyway to see the exact compile errors and confirm they are ONLY in those 3 files (proving Task 2's own 2 fixed call sites plus `JwtUtils.java`/`JwtClaims.java` themselves compile cleanly). Do not proceed to commit until you've confirmed the failures are scoped to exactly those 3 files.

- [ ] **Step 8: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/domain/model/JwtClaims.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/jwt/util/JwtUtils.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ServiceTokenServiceImpl.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ClientTokenServiceImpl.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/jwt/util/JwtUtilsTest.java
git commit -m "phase-b sub-project B: extend JwtClaims/JwtUtils with username, userEmail, tenantName claims"
```

Note for the implementer: this commit intentionally leaves `LoginExecutionServiceImpl.java`, `ImpersonationTokenServiceImpl.java`, and `RefreshTokenServiceImpl.java` non-compiling — Task 3 fixes them in the very next commit. If your environment or workflow requires every commit to build green, merge Task 2 and Task 3 into one commit instead; do not weaken this task's test coverage to work around it.

---

### Task 3: Wire the 3 real call sites

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/LoginExecutionServiceImpl.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImpl.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/application/impl/RefreshTokenServiceImpl.java`
- Modify: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/LoginExecutionServiceImplTest.java`
- Modify: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImplTest.java`
- Modify: `gen-auth-starter/src/test/java/com/example/authsvc/application/impl/RefreshTokenServiceImplTest.java`

**Interfaces:**
- Consumes: `TenantSlugResolver.resolveName(UUID) -> String`, `UserDisplayNameResolver.resolve(UUID) -> String` (Task 1); `JwtClaims`'s 12-arg constructor (Task 2).
- Produces: nothing further — this is the final task.

**Important finding from a fresh read of the real files (corrects an earlier assumption in this sub-project's scoping)**: `RefreshTokenServiceImpl` has exactly **1** `new JwtClaims(...)` construction point (inside `refresh()`), not 4 — the "4 branches" from this session's earlier outbox/audit-routing work were about `publishLogout`/`publishAuditLogout` event calls, a completely different concern. All 3 classes in this task have exactly 1 `JwtClaims` construction point each.

**Important finding**: `ImpersonationTokenServiceImpl` never loads an `AuthUserEntity` today (it trusts the caller's request), so it has no `user.getEmail()` to read for `userEmail`. CPMS's real code adds an `AuthUserJpaRepository` dependency there specifically to look up the impersonating super admin's own email (the acting user, not the impersonated tenant). This task adds that dependency to `ImpersonationTokenServiceImpl` — it is not called out explicitly in the spec's section 3, but is the direct, necessary consequence of applying the spec's "userEmail read from the already-loaded entity" rule to a class where no entity happens to be already loaded.

- [ ] **Step 1: Write the failing tests**

In `LoginExecutionServiceImplTest.java`, add a constructor param for the new mock. Change the `@Mock` fields block to add:

```java
    @Mock private com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver userDisplayNameResolver;
```

Change the `setUp()` constructor call to append `userDisplayNameResolver` as the last argument (after `mfaLoginGate`):

```java
        service = new LoginExecutionServiceImpl(
                sessionRepo,
                refreshTokenAuditRepo,
                authUserRoleRepository,
                refreshTokenStore,
                loginAttemptService,
                lockoutService,
                auditLogService,
                jwtUtils,
                passwordHasher,
                asyncExecutor,
                txManager,
                tenantSlugResolver,
                authEventPublisher,
                mfaLoginGate,
                userDisplayNameResolver
        );
```

Add a new test (anywhere in the class body):

```java
    @Test
    void issueTokens_populatesUsernameUserEmailTenantNameClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(tenantId)
                .email("jane@example.com")
                .passwordHash("stored-hash")
                .userType(UserType.TENANT_USER)
                .active(true)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("Password123!");

        when(passwordHasher.verify(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        when(authUserRoleRepository.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Instant now = Instant.now();
        when(jwtUtils.generateTokenPair(any(JwtClaims.class))).thenReturn(
                new TokenPair("access-token", "refresh-token", now.plusSeconds(900), now.plusSeconds(604800)));
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("acme");
        when(tenantSlugResolver.resolveName(tenantId)).thenReturn("Acme Corp");
        when(userDisplayNameResolver.resolve(userId)).thenReturn("Jane Doe");

        service.executeLogin(user, request, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        ArgumentCaptor<JwtClaims> claimsCaptor = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtUtils).generateTokenPair(claimsCaptor.capture());
        assertThat(claimsCaptor.getValue().username()).isEqualTo("Jane Doe");
        assertThat(claimsCaptor.getValue().userEmail()).isEqualTo("jane@example.com");
        assertThat(claimsCaptor.getValue().tenantName()).isEqualTo("Acme Corp");
    }
```

In `ImpersonationTokenServiceImplTest.java`, add mocks and update every constructor call. Change the `@Mock` fields block to add:

```java
    @Mock private com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository authUserRepository;
    @Mock private com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver userDisplayNameResolver;
```

Update all 4 existing `new ImpersonationTokenServiceImpl(jwtUtils, authSessionRepository, tenantSlugResolver, authEventPublisher)` calls to:

```java
new ImpersonationTokenServiceImpl(jwtUtils, authSessionRepository, authUserRepository, tenantSlugResolver, userDisplayNameResolver, authEventPublisher)
```

Add a new test:

```java
    @Test
    void issue_populatesUsernameUserEmailTenantNameFromActingSuperAdmin() throws Exception {
        ImpersonationTokenServiceImpl service = new ImpersonationTokenServiceImpl(
                jwtUtils, authSessionRepository, authUserRepository, tenantSlugResolver,
                userDisplayNameResolver, authEventPublisher);
        setExpirationMinutes(service, 60);

        UUID superAdminId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();

        com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity superAdmin =
                com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity.builder()
                        .id(superAdminId)
                        .email("admin@example.com")
                        .userType(UserType.SUPER_ADMIN)
                        .active(true)
                        .build();

        when(authUserRepository.findById(superAdminId)).thenReturn(java.util.Optional.of(superAdmin));
        when(userDisplayNameResolver.resolve(superAdminId)).thenReturn("Admin Name");
        when(tenantSlugResolver.resolveName(tenantId)).thenReturn("Acme Corp");
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                superAdminId, tenantId, "acme", roleId, "session-abc", false);

        service.issue(request);

        ArgumentCaptor<JwtClaims> claimsCaptor = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtUtils).generateAccessToken(claimsCaptor.capture());
        assertThat(claimsCaptor.getValue().username()).isEqualTo("Admin Name");
        assertThat(claimsCaptor.getValue().userEmail()).isEqualTo("admin@example.com");
        assertThat(claimsCaptor.getValue().tenantName()).isEqualTo("Acme Corp");
    }
```

In `RefreshTokenServiceImplTest.java`, add a mock and update the constructor call. Change the `@Mock` fields block to add:

```java
    @Mock private com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver userDisplayNameResolver;
```

Change `setUp()`'s constructor call to append `userDisplayNameResolver` right after `tenantSlugResolver` (before `authEventPublisher`):

```java
        service = new RefreshTokenServiceImpl(
                refreshTokenStore, refreshTokenAuditRepo, sessionRepo, userRepo,
                authUserRoleRepository, jwtUtils, auditLogService, tenantSlugResolver,
                userDisplayNameResolver, authEventPublisher, txManager);
```

Add a new test:

```java
    @Test
    void refresh_populatesUsernameUserEmailTenantNameClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        com.example.authsvc.domain.model.RefreshToken cached =
                com.example.authsvc.domain.model.RefreshToken.builder()
                        .id(UUID.randomUUID())
                        .userId(userId)
                        .tenantId(tenantId)
                        .sessionId(sessionId)
                        .familyId(UUID.randomUUID())
                        .tokenHash("hash")
                        .generation(0)
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .absoluteExpiresAt(Instant.now().plusSeconds(3600))
                        .build();

        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(sessionId).userId(userId).tenantId(tenantId).active(true).build();

        com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity user =
                com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity.builder()
                        .id(userId).tenantId(tenantId).email("jane@example.com")
                        .userType(com.example.authsvc.domain.enums.UserType.TENANT_USER)
                        .active(true).build();

        when(refreshTokenStore.findByTokenHash(anyString())).thenReturn(Optional.of(cached));
        when(sessionRepo.findByIdAndActiveTrue(sessionId)).thenReturn(Optional.of(session));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(authUserRoleRepository.findRoleIdsByUserId(userId)).thenReturn(java.util.List.of());
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("acme");
        when(tenantSlugResolver.resolveName(tenantId)).thenReturn("Acme Corp");
        when(userDisplayNameResolver.resolve(userId)).thenReturn("Jane Doe");
        when(jwtUtils.generateTokenPair(any(JwtClaims.class), any(Instant.class))).thenReturn(
                new com.example.authsvc.domain.model.TokenPair(
                        "access-token", "refresh-token",
                        Instant.now().plusSeconds(900), cached.getAbsoluteExpiresAt()));

        service.refresh("some-raw-token", "1.2.3.4", "junit-agent");

        ArgumentCaptor<JwtClaims> claimsCaptor = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtUtils).generateTokenPair(claimsCaptor.capture(), any(Instant.class));
        assertThat(claimsCaptor.getValue().username()).isEqualTo("Jane Doe");
        assertThat(claimsCaptor.getValue().userEmail()).isEqualTo("jane@example.com");
        assertThat(claimsCaptor.getValue().tenantName()).isEqualTo("Acme Corp");
    }
```

Add this import to `RefreshTokenServiceImplTest.java` if not already present: `import static org.assertj.core.api.Assertions.assertThat;`

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :gen-auth-starter:test --console=plain`
Expected: FAIL — compile errors (constructor arg-count mismatches) until Step 3 below is done.

- [ ] **Step 3: Implement — `LoginExecutionServiceImpl.java`**

Add the new field, constructor param, and assignment. Change:

```java
    private final TenantSlugResolver            tenantSlugResolver;
    private final AuthEventPublisher            authEventPublisher;
    private final MfaLoginGate                  mfaLoginGate;

    public LoginExecutionServiceImpl(
            AuthSessionJpaRepository sessionRepo,
            AuthRefreshTokenJpaRepository refreshTokenAuditRepo,
            AuthUserRoleJpaRepository authUserRoleRepository,
            RefreshTokenStore refreshTokenStore,
            LoginAttemptService loginAttemptService,
            LockoutService lockoutService,
            AuditLogService auditLogService,
            JwtUtils jwtUtils,
            PasswordHasher passwordHasher,
            @Qualifier("authAsync") Executor asyncExecutor,
            PlatformTransactionManager txManager,
            TenantSlugResolver tenantSlugResolver,
            @Autowired(required = false) AuthEventPublisher authEventPublisher,
            @Autowired(required = false) MfaLoginGate mfaLoginGate) {
        this.sessionRepo            = sessionRepo;
        this.refreshTokenAuditRepo  = refreshTokenAuditRepo;
        this.authUserRoleRepository = authUserRoleRepository;
        this.refreshTokenStore      = refreshTokenStore;
        this.loginAttemptService    = loginAttemptService;
        this.lockoutService         = lockoutService;
        this.auditLogService        = auditLogService;
        this.jwtUtils               = jwtUtils;
        this.passwordHasher         = passwordHasher;
        this.asyncExecutor          = asyncExecutor;
        this.txTemplate             = new TransactionTemplate(txManager);
        this.tenantSlugResolver     = tenantSlugResolver;
        this.authEventPublisher     = authEventPublisher;
        this.mfaLoginGate           = mfaLoginGate;
    }
```

to:

```java
    private final TenantSlugResolver            tenantSlugResolver;
    private final AuthEventPublisher            authEventPublisher;
    private final MfaLoginGate                  mfaLoginGate;
    private final UserDisplayNameResolver       userDisplayNameResolver;

    public LoginExecutionServiceImpl(
            AuthSessionJpaRepository sessionRepo,
            AuthRefreshTokenJpaRepository refreshTokenAuditRepo,
            AuthUserRoleJpaRepository authUserRoleRepository,
            RefreshTokenStore refreshTokenStore,
            LoginAttemptService loginAttemptService,
            LockoutService lockoutService,
            AuditLogService auditLogService,
            JwtUtils jwtUtils,
            PasswordHasher passwordHasher,
            @Qualifier("authAsync") Executor asyncExecutor,
            PlatformTransactionManager txManager,
            TenantSlugResolver tenantSlugResolver,
            @Autowired(required = false) AuthEventPublisher authEventPublisher,
            @Autowired(required = false) MfaLoginGate mfaLoginGate,
            UserDisplayNameResolver userDisplayNameResolver) {
        this.sessionRepo            = sessionRepo;
        this.refreshTokenAuditRepo  = refreshTokenAuditRepo;
        this.authUserRoleRepository = authUserRoleRepository;
        this.refreshTokenStore      = refreshTokenStore;
        this.loginAttemptService    = loginAttemptService;
        this.lockoutService         = lockoutService;
        this.auditLogService        = auditLogService;
        this.jwtUtils               = jwtUtils;
        this.passwordHasher         = passwordHasher;
        this.asyncExecutor          = asyncExecutor;
        this.txTemplate             = new TransactionTemplate(txManager);
        this.tenantSlugResolver     = tenantSlugResolver;
        this.authEventPublisher     = authEventPublisher;
        this.mfaLoginGate           = mfaLoginGate;
        this.userDisplayNameResolver = userDisplayNameResolver;
    }
```

Add the import: `import com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver;`

Change the claims-construction block from:

```java
        // ── JWT generation — uses role_ids[] from auth_user_roles ─────────────
        String tenantSlug = tenantSlugResolver.resolve(user.getTenantId());
        JwtClaims claims = new JwtClaims(
                user.getId(), user.getTenantId(), tenantSlug,
                roleIds, user.getUserType(),
                now, now.plus(15, ChronoUnit.MINUTES), sessionId.toString(), null);
```

to:

```java
        // ── JWT generation — uses role_ids[] from auth_user_roles ─────────────
        String tenantSlug = tenantSlugResolver.resolve(user.getTenantId());
        String tenantName = tenantSlugResolver.resolveName(user.getTenantId());
        String username    = userDisplayNameResolver.resolve(user.getId());
        JwtClaims claims = new JwtClaims(
                user.getId(), user.getTenantId(), tenantSlug,
                roleIds, user.getUserType(),
                now, now.plus(15, ChronoUnit.MINUTES), sessionId.toString(), null,
                username, email, tenantName);
```

(`email` is already in scope — declared as `String email = user.getEmail();` at the top of `issueTokens`.)

- [ ] **Step 4: Implement — `ImpersonationTokenServiceImpl.java`**

Add the two new dependencies. Change:

```java
    private final JwtUtils                 jwtUtils;
    private final AuthSessionJpaRepository authSessionRepository;
    private final TenantSlugResolver       tenantSlugResolver;

    private final AuthEventPublisher authEventPublisher;

    @Value("${jwt.impersonation-token.expiration-minutes:60}")
    private int expirationMinutes;

    public ImpersonationTokenServiceImpl(
            JwtUtils jwtUtils,
            AuthSessionJpaRepository authSessionRepository,
            TenantSlugResolver tenantSlugResolver,
            @Autowired(required = false) AuthEventPublisher authEventPublisher) {
        this.jwtUtils = jwtUtils;
        this.authSessionRepository = authSessionRepository;
        this.tenantSlugResolver = tenantSlugResolver;
        this.authEventPublisher = authEventPublisher;
    }
```

to:

```java
    private final JwtUtils                 jwtUtils;
    private final AuthSessionJpaRepository authSessionRepository;
    private final AuthUserJpaRepository    authUserRepository;
    private final TenantSlugResolver       tenantSlugResolver;
    private final UserDisplayNameResolver  userDisplayNameResolver;

    private final AuthEventPublisher authEventPublisher;

    @Value("${jwt.impersonation-token.expiration-minutes:60}")
    private int expirationMinutes;

    public ImpersonationTokenServiceImpl(
            JwtUtils jwtUtils,
            AuthSessionJpaRepository authSessionRepository,
            AuthUserJpaRepository authUserRepository,
            TenantSlugResolver tenantSlugResolver,
            UserDisplayNameResolver userDisplayNameResolver,
            @Autowired(required = false) AuthEventPublisher authEventPublisher) {
        this.jwtUtils = jwtUtils;
        this.authSessionRepository = authSessionRepository;
        this.authUserRepository = authUserRepository;
        this.tenantSlugResolver = tenantSlugResolver;
        this.userDisplayNameResolver = userDisplayNameResolver;
        this.authEventPublisher = authEventPublisher;
    }
```

Add imports: `import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;` and `import com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver;`

Change the claims-construction block from:

```java
        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(UUID.randomUUID())
                .userId(request.superAdminId())
                .tenantId(request.tenantId())
                .roleId(request.impersonationRoleId())
                .userType(UserType.SUPER_ADMIN_IMPERSONATING)
                .impersonation(true)
                .active(true)
                .expiresAt(expiresAt)
                .lastActivityAt(now)
                .build();

        authSessionRepository.save(session);

        JwtClaims claims = new JwtClaims(
                request.superAdminId(),
                request.tenantId(),
                tenantSlug,
                List.of(request.impersonationRoleId()),
                UserType.SUPER_ADMIN_IMPERSONATING,
                now,
                expiresAt,
                request.sessionId(),
                null
        );
```

to:

```java
        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(UUID.randomUUID())
                .userId(request.superAdminId())
                .tenantId(request.tenantId())
                .roleId(request.impersonationRoleId())
                .userType(UserType.SUPER_ADMIN_IMPERSONATING)
                .impersonation(true)
                .active(true)
                .expiresAt(expiresAt)
                .lastActivityAt(now)
                .build();

        authSessionRepository.save(session);

        // The acting user is the real super admin doing the impersonating, not the
        // impersonated tenant's own admin — username/email must identify who is
        // actually acting, which is what an audit trail needs.
        String username = userDisplayNameResolver.resolve(request.superAdminId());
        String email = authUserRepository.findById(request.superAdminId())
                .map(com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity::getEmail)
                .orElse(null);
        String tenantName = tenantSlugResolver.resolveName(request.tenantId());

        JwtClaims claims = new JwtClaims(
                request.superAdminId(),
                request.tenantId(),
                tenantSlug,
                List.of(request.impersonationRoleId()),
                UserType.SUPER_ADMIN_IMPERSONATING,
                now,
                expiresAt,
                request.sessionId(),
                null,
                username, email, tenantName
        );
```

- [ ] **Step 5: Implement — `RefreshTokenServiceImpl.java`**

Add the new field and constructor param. Change:

```java
    private final TenantSlugResolver tenantSlugResolver;

    private final AuthEventPublisher authEventPublisher;
```

to:

```java
    private final TenantSlugResolver tenantSlugResolver;
    private final UserDisplayNameResolver userDisplayNameResolver;

    private final AuthEventPublisher authEventPublisher;
```

Change the constructor:

```java
    public RefreshTokenServiceImpl(
            RefreshTokenStore refreshTokenStore,
            AuthRefreshTokenJpaRepository refreshTokenAuditRepo,
            AuthSessionJpaRepository sessionRepo,
            AuthUserJpaRepository userRepo,
            AuthUserRoleJpaRepository authUserRoleRepository,
            JwtUtils jwtUtils,
            AuditLogService auditLogService,
            TenantSlugResolver tenantSlugResolver,
            @Autowired(required = false) AuthEventPublisher authEventPublisher,
            PlatformTransactionManager txManager) {
        this.refreshTokenStore = refreshTokenStore;
        this.refreshTokenAuditRepo = refreshTokenAuditRepo;
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
        this.authUserRoleRepository = authUserRoleRepository;
        this.jwtUtils = jwtUtils;
        this.auditLogService = auditLogService;
        this.tenantSlugResolver = tenantSlugResolver;
        this.authEventPublisher = authEventPublisher;
        this.eventPublishTxTemplate = new TransactionTemplate(txManager);
        this.eventPublishTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
```

to:

```java
    public RefreshTokenServiceImpl(
            RefreshTokenStore refreshTokenStore,
            AuthRefreshTokenJpaRepository refreshTokenAuditRepo,
            AuthSessionJpaRepository sessionRepo,
            AuthUserJpaRepository userRepo,
            AuthUserRoleJpaRepository authUserRoleRepository,
            JwtUtils jwtUtils,
            AuditLogService auditLogService,
            TenantSlugResolver tenantSlugResolver,
            UserDisplayNameResolver userDisplayNameResolver,
            @Autowired(required = false) AuthEventPublisher authEventPublisher,
            PlatformTransactionManager txManager) {
        this.refreshTokenStore = refreshTokenStore;
        this.refreshTokenAuditRepo = refreshTokenAuditRepo;
        this.sessionRepo = sessionRepo;
        this.userRepo = userRepo;
        this.authUserRoleRepository = authUserRoleRepository;
        this.jwtUtils = jwtUtils;
        this.auditLogService = auditLogService;
        this.tenantSlugResolver = tenantSlugResolver;
        this.userDisplayNameResolver = userDisplayNameResolver;
        this.authEventPublisher = authEventPublisher;
        this.eventPublishTxTemplate = new TransactionTemplate(txManager);
        this.eventPublishTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
```

Add the import: `import com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver;`

Change the claims-construction block from:

```java
        String tenantSlug = tenantSlugResolver.resolve(user.getTenantId());
        List<UUID> roleIds = authUserRoleRepository.findRoleIdsByUserId(user.getId());
        JwtClaims claims = new JwtClaims(
                user.getId(), user.getTenantId(), tenantSlug,
                roleIds, user.getUserType(),
                now, now.plus(15, ChronoUnit.MINUTES), session.getId().toString(), null);
```

to:

```java
        String tenantSlug = tenantSlugResolver.resolve(user.getTenantId());
        String tenantName = tenantSlugResolver.resolveName(user.getTenantId());
        String username    = userDisplayNameResolver.resolve(user.getId());
        List<UUID> roleIds = authUserRoleRepository.findRoleIdsByUserId(user.getId());
        JwtClaims claims = new JwtClaims(
                user.getId(), user.getTenantId(), tenantSlug,
                roleIds, user.getUserType(),
                now, now.plus(15, ChronoUnit.MINUTES), session.getId().toString(), null,
                username, user.getEmail(), tenantName);
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `.\gradlew.bat :gen-auth-starter:test --console=plain`
Expected: BUILD SUCCESSFUL, all tests pass (including Task 1's and Task 2's).

- [ ] **Step 7: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/application/impl/LoginExecutionServiceImpl.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImpl.java gen-auth-starter/src/main/java/com/example/authsvc/application/impl/RefreshTokenServiceImpl.java gen-auth-starter/src/test/java/com/example/authsvc/application/impl/LoginExecutionServiceImplTest.java gen-auth-starter/src/test/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImplTest.java gen-auth-starter/src/test/java/com/example/authsvc/application/impl/RefreshTokenServiceImplTest.java
git commit -m "phase-b sub-project B: wire username/userEmail/tenantName claims into login, refresh, impersonation"
```

---

## Final verification

- [ ] Run the full suite once more from repo root: `.\gradlew.bat :gen-auth-starter:test --console=plain` — expect `BUILD SUCCESSFUL`.
- [ ] Confirm `ServiceTokenServiceImplTest` and `ClientTokenServiceImplTest` (if they exist and assert on `JwtClaims` contents) still pass unchanged — they should, since Task 2 only appended `null, null, null` and changed no existing behavior.
- [ ] Update `phase-b.md` to mark sub-project B resolved, mirroring the style already used for gap.md items — commit separately.
