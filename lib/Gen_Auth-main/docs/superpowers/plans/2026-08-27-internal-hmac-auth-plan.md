# Pluggable HMAC Internal-Auth Strategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a second, pluggable internal-caller authentication mechanism — HMAC-signed requests — to `gen-auth-starter`, alongside the existing `X-Internal-Secret` shared-header check, and prove it interoperates byte-for-byte with CPMS-Platform's real SUP-SVC caller.

**Architecture:** A new `InternalHmacAuthFilter` (config-driven target paths, not hardcoded) runs alongside the existing `InternalTokenAuthFilter` — same non-interacting-filters shape CPMS's own `auth-svc` already uses (each filter self-selects via disjoint path sets). A new `V1InternalTokenController` exposes `POST /v1/impersonation-token`, reusing the existing `ImpersonationTokenService` unchanged, reachable only through this new HMAC-guarded path — the existing `/internal/auth/impersonation-token` shared-secret path is untouched.

**Tech Stack:** Spring Boot 4 (`OncePerRequestFilter`, `@ConditionalOnProperty` gating, `FilterRegistrationBean` double-registration suppression), `javax.crypto.Mac`/`HmacSHA256` (JDK-native, no new dependency), JUnit 5 + Mockito + AssertJ + Spring's `MockHttpServletRequest`/`MockHttpServletResponse`.

**Spec:** `docs/superpowers/specs/2026-08-27-internal-hmac-auth-design.md`

## Global Constraints

- Wire format is **verified against real code in CPMS-Platform, not designed** — do not change it: headers `X-CPMS-Service` (logged only, never checked), `X-CPMS-Timestamp` (unix seconds), `X-CPMS-Signature` (hex); `signature = HMAC-SHA256("{timestamp}:{rawBody}", internal-service-secret)`; 60-second clock-skew tolerance; a body-less request signs against the literal string `"{}"`.
- **Deliberate deviation from auth-svc's own code**: use `StandardCharsets.UTF_8` when getting bytes for the HMAC computation, not `US_ASCII` — this matches what the real TS caller actually signs (Node's `createHmac(...).update(string)` defaults to UTF-8); auth-svc's own `US_ASCII` choice is a latent, unexercised bug, not part of the protocol.
- Reuses the **same** `internal-service-secret` property `InternalTokenAuthFilter` already requires — no new secret to provision.
- `InternalHmacAuthFilter` must be safe to always register (no enable flag on the `@Component` itself) — it no-ops whenever `app.internal-hmac-auth.enabled=false` or the request path isn't in `app.internal-hmac-auth.targetPaths`. This mirrors `InternalTokenAuthFilter`'s own unconditional-registration style.
- `V1InternalTokenController` is only registered (via `@ConditionalOnProperty`) when **both** `app.super-admin.enabled=true` (the underlying feature's existing gate — `ImpersonationTokenServiceImpl` itself won't exist otherwise) and `app.internal-hmac-auth.enabled=true`. If either is off, `/v1/impersonation-token` 404s rather than existing half-configured.
- Existing `/internal/auth/impersonation-token` (shared-secret) is untouched — this is an additive second path, not a replacement.
- `POST /v1/client-token` and any other future HMAC-guarded path are out of scope — they become their own sub-project, which just adds itself to `app.internal-hmac-auth.targetPaths`.
- No change to CPMS-Platform itself — that repo is read-only reference for this work.

---

### Task 1: `InternalHmacAuthProperties` and `InternalHmacAuthFilter`

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/properties/InternalHmacAuthProperties.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/filter/InternalHmacAuthFilter.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/filter/InternalHmacAuthFilterTest.java`

**Interfaces:**
- Produces: `InternalHmacAuthProperties` — `boolean enabled` (default `false`), `List<String> targetPaths` (default empty `ArrayList`), bound from `app.internal-hmac-auth.*`.
- Produces: `InternalHmacAuthFilter` (constructor takes `InternalHmacAuthProperties`, plus a field-injected `@Value("${internal-service-secret:}") String secret` — same pattern `ImpersonationTokenServiceImpl` already uses for mixing constructor-injected collaborators with a `@Value` field). Consumed by `SecurityConfig` (Task 3) via `addFilterBefore(...)`.

- [ ] **Step 1: Write the failing test**

```java
package com.example.authsvc.infrastructure.security.filter;

import com.example.authsvc.config.properties.InternalHmacAuthProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class InternalHmacAuthFilterTest {

    private static final String SECRET = "test-internal-service-secret";

    @Mock private FilterChain filterChain;

    private InternalHmacAuthFilter filter;
    private InternalHmacAuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new InternalHmacAuthProperties();
        properties.setEnabled(true);
        properties.setTargetPaths(List.of("/v1/impersonation-token"));
        filter = new InternalHmacAuthFilter(properties);
        ReflectionTestUtils.setField(filter, "secret", SECRET);
    }

    /**
     * Computes a signature the exact way CPMS-Platform's real TS caller does
     * (sup-svc/src/infra/external/auth-svc.client.ts, requestImpersonationToken):
     * HMAC-SHA256 over "{timestamp}:{utf8-body}". This is the test that proves
     * byte-for-byte interop, not just "the filter works."
     */
    private static String signLikeRealCaller(String timestamp, String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal((timestamp + ":" + body).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(raw);
    }

    @Test
    void validSignatureFromRealCallerAlgorithm_passesThroughWithBodyStillReadable() throws Exception {
        String body = "{\"superAdminId\":\"11111111-1111-1111-1111-111111111111\"}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String signature = signLikeRealCaller(timestamp, body, SECRET);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/impersonation-token");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-CPMS-Timestamp", timestamp);
        request.addHeader("X-CPMS-Signature", signature);
        request.addHeader("X-CPMS-Service", "sup-svc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(ArgumentMatchers.argThat(req -> {
            try {
                return new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8).equals(body);
            } catch (Exception e) {
                return false;
            }
        }), ArgumentMatchers.eq(response));
    }

    @Test
    void missingSignatureHeader_rejectsWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/impersonation-token");
        request.addHeader("X-CPMS-Timestamp", String.valueOf(System.currentTimeMillis() / 1000L));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void staleTimestamp_rejectsWith401() throws Exception {
        String body = "{}";
        String staleTimestamp = String.valueOf((System.currentTimeMillis() / 1000L) - 120);
        String signature = signLikeRealCaller(staleTimestamp, body, SECRET);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/impersonation-token");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-CPMS-Timestamp", staleTimestamp);
        request.addHeader("X-CPMS-Signature", signature);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void wrongSignature_rejectsWith401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/impersonation-token");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-CPMS-Timestamp", String.valueOf(System.currentTimeMillis() / 1000L));
        request.addHeader("X-CPMS-Signature", "0".repeat(64));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    void pathNotInTargetPaths_skipsVerificationEntirely() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/client-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void disabled_skipsVerificationEntirely() throws Exception {
        properties.setEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/impersonation-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void emptyBody_signsAgainstLiteralEmptyObjectString() throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String signature = signLikeRealCaller(timestamp, "{}", SECRET);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/impersonation-token");
        request.addHeader("X-CPMS-Timestamp", timestamp);
        request.addHeader("X-CPMS-Signature", signature);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(ArgumentMatchers.any(), ArgumentMatchers.eq(response));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.security.filter.InternalHmacAuthFilterTest"`
Expected: FAIL (compilation error) — neither class exists yet.

- [ ] **Step 3: Write `InternalHmacAuthProperties`**

```java
package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Gates and configures the pluggable HMAC internal-auth strategy, bound from
 * {@code app.internal-hmac-auth.*}. Off by default — {@link InternalHmacAuthFilter}
 * is still always registered (matches {@code InternalTokenAuthFilter}'s own
 * unconditional style) but no-ops until this is enabled and a request's path
 * is listed in {@link #targetPaths}.
 */
@Data
@ConfigurationProperties(prefix = "app.internal-hmac-auth")
public class InternalHmacAuthProperties {

    private boolean enabled = false;

    /** Exact request paths this filter verifies HMAC signatures on — e.g. {@code /v1/impersonation-token}. */
    private List<String> targetPaths = new ArrayList<>();
}
```

- [ ] **Step 4: Write `InternalHmacAuthFilter`**

```java
package com.example.authsvc.infrastructure.security.filter;

import com.example.authsvc.config.properties.InternalHmacAuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verifies HMAC-signed internal requests — a second, pluggable internal-auth
 * strategy alongside {@link InternalTokenAuthFilter}'s shared-secret header
 * check. Only active for paths listed in {@code app.internal-hmac-auth.target-paths}
 * when {@code app.internal-hmac-auth.enabled=true}; a no-op otherwise, so it's
 * always safe to leave registered.
 *
 * <p>Wire format (verified against a real CPMS-Platform caller, not designed
 * from scratch): the caller sends {@code X-CPMS-Timestamp} (unix seconds) and
 * {@code X-CPMS-Signature} (hex), where {@code signature =
 * HMAC-SHA256("{timestamp}:{rawBody}", secret)} — a body-less request signs
 * against the literal string {@code "{}"}. {@code X-CPMS-Service} is accepted
 * and logged but never used as an authorization check (matches upstream
 * behavior — it's telemetry, not a trust boundary).
 *
 * <p>Uses UTF-8 (not the {@code US_ASCII} the upstream verifier this was
 * ported from happens to use) — this matches what the real signer actually
 * produces (Node's {@code createHmac(...).update(string)} defaults to UTF-8);
 * the upstream choice is a latent, unexercised discrepancy, not part of the
 * protocol.
 *
 * <p>Reuses the same {@code internal-service-secret} {@link InternalTokenAuthFilter}
 * already requires — no separate secret to provision.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalHmacAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_SERVICE        = "X-CPMS-Service";
    private static final String HEADER_TIMESTAMP       = "X-CPMS-Timestamp";
    private static final String HEADER_SIGNATURE       = "X-CPMS-Signature";
    private static final long   MAX_CLOCK_SKEW_SECONDS = 60L;
    private static final String HMAC_ALGORITHM         = "HmacSHA256";
    private static final String EMPTY_BODY             = "{}";

    private final InternalHmacAuthProperties properties;

    @Value("${internal-service-secret:}")
    private String secret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !properties.getTargetPaths().contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (secret == null || secret.isBlank()) {
            log.error("internal_hmac_auth.secret_not_configured path={}", request.getServletPath());
            sendUnauthorized(response);
            return;
        }

        String service   = request.getHeader(HEADER_SERVICE);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);
        String signature = request.getHeader(HEADER_SIGNATURE);

        if (timestamp == null || signature == null) {
            log.warn("internal_hmac_auth.missing_headers path={} service={}", request.getServletPath(), service);
            sendUnauthorized(response);
            return;
        }

        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            log.warn("internal_hmac_auth.invalid_timestamp path={} value={}", request.getServletPath(), timestamp);
            sendUnauthorized(response);
            return;
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (Math.abs(nowSeconds - timestampSeconds) > MAX_CLOCK_SKEW_SECONDS) {
            log.warn("internal_hmac_auth.stale_timestamp path={} skewSeconds={}",
                    request.getServletPath(), Math.abs(nowSeconds - timestampSeconds));
            sendUnauthorized(response);
            return;
        }

        byte[] bodyBytes = request.getInputStream().readAllBytes();
        String body = bodyBytes.length == 0 ? EMPTY_BODY : new String(bodyBytes, StandardCharsets.UTF_8);

        String expectedSignature;
        try {
            expectedSignature = hmacSha256Hex(timestamp + ":" + body, secret);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("internal_hmac_auth.signature_computation_failed path={}", request.getServletPath(), e);
            sendUnauthorized(response);
            return;
        }

        if (!constantTimeEquals(expectedSignature, signature)) {
            log.warn("internal_hmac_auth.signature_mismatch path={} service={}", request.getServletPath(), service);
            sendUnauthorized(response);
            return;
        }

        log.debug("internal_hmac_auth.verified path={} service={}", request.getServletPath(), service);
        chain.doFilter(new CachedBodyRequestWrapper(request, bodyBytes), response);
    }

    private static String hmacSha256Hex(String data, String key) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    /** Constant-time comparison to prevent timing attacks. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private static void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Unauthorized\"}");
    }

    /** Re-readable request wrapper — the body is consumed once here for HMAC verification, then replayed for the controller. */
    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.cachedBody = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override public int     read()                          { return bais.read(); }
                @Override public boolean isFinished()                    { return bais.available() == 0; }
                @Override public boolean isReady()                       { return true; }
                @Override public void    setReadListener(ReadListener l) {}
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.infrastructure.security.filter.InternalHmacAuthFilterTest"`
Expected: PASS (7/7)

- [ ] **Step 6: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/config/properties/InternalHmacAuthProperties.java gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/filter/InternalHmacAuthFilter.java gen-auth-starter/src/test/java/com/example/authsvc/infrastructure/security/filter/InternalHmacAuthFilterTest.java
git commit -m "add pluggable InternalHmacAuthFilter, verified against CPMS-Platform's real wire format"
```

---

### Task 2: `V1InternalTokenController`

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/api/controller/V1InternalTokenController.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/api/controller/V1InternalTokenControllerTest.java`
- Test: `gen-auth-starter/src/test/java/com/example/authsvc/api/controller/V1InternalTokenControllerConditionalRegistrationTest.java`

**Interfaces:**
- Consumes: `ImpersonationTokenService.issue(ImpersonationTokenRequest)` → `ImpersonationTokenResponse` (existing, unchanged).
- Produces: `POST /v1/impersonation-token` — same request/response DTOs as the existing `/internal/auth/impersonation-token` endpoint. Consumed by `SecurityConfig`'s permitAll entry (Task 3) and, at real cutover time, CPMS-Platform's SUP-SVC (`auth-svc.client.ts`'s `requestImpersonationToken`, unmodified).

- [ ] **Step 1: Write the failing test**

```java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.application.service.ImpersonationTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V1InternalTokenControllerTest {

    @Mock private ImpersonationTokenService impersonationTokenService;

    private V1InternalTokenController controller;

    @BeforeEach
    void setUp() {
        controller = new V1InternalTokenController(impersonationTokenService);
    }

    @Test
    void issueImpersonationToken_delegatesToServiceAndReturnsItsResponse() {
        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                UUID.randomUUID(), UUID.randomUUID(), "acme", UUID.randomUUID(), "session-abc", false);
        ImpersonationTokenResponse expected = new ImpersonationTokenResponse("jwt-value", 3600, "session-abc");
        when(impersonationTokenService.issue(request)).thenReturn(expected);

        ResponseEntity<ImpersonationTokenResponse> result = controller.issueImpersonationToken(request);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isEqualTo(expected);
        verify(impersonationTokenService).issue(eq(request));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.api.controller.V1InternalTokenControllerTest"`
Expected: FAIL (compilation error) — `V1InternalTokenController` doesn't exist yet.

- [ ] **Step 3: Write the controller**

```java
package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.application.service.ImpersonationTokenService;
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
 * HMAC-guarded internal token-mint endpoints, matching CPMS auth-svc's
 * {@code /v1/*} path shape so existing HMAC-signing callers (e.g. SUP-SVC's
 * {@code requestImpersonationToken}) work against this service unmodified.
 * Guarded by {@link com.example.authsvc.infrastructure.security.filter.InternalHmacAuthFilter}
 * via {@code app.internal-hmac-auth.target-paths} — not by JWT, not by the
 * shared-secret filter. See {@code SecurityConfig}'s permitAll entry for this path.
 *
 * <p>Reuses the exact same {@link ImpersonationTokenService} the existing
 * {@code /internal/auth/impersonation-token} endpoint calls — identical
 * business logic and response shape, just a different path and auth
 * mechanism for callers that sign requests instead of sending a shared secret.
 *
 * <p>Only registered when both the underlying feature ({@code app.super-admin.enabled})
 * and this HMAC mechanism ({@code app.internal-hmac-auth.enabled}) are on — if
 * either is off, this path 404s rather than existing half-configured.
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@ConditionalOnProperty(prefix = "app", name = {"super-admin.enabled", "internal-hmac-auth.enabled"}, havingValue = "true")
@RequiredArgsConstructor
public class V1InternalTokenController {

    private final ImpersonationTokenService impersonationTokenService;

    @PostMapping("/impersonation-token")
    public ResponseEntity<ImpersonationTokenResponse> issueImpersonationToken(
            @Valid @RequestBody ImpersonationTokenRequest request) {

        log.info("v1.impersonation.token.request superAdminId={} tenantId={} sessionId={}",
                request.superAdminId(), request.tenantId(), request.sessionId());

        ImpersonationTokenResponse response = impersonationTokenService.issue(request);

        return ResponseEntity.ok(response);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.api.controller.V1InternalTokenControllerTest"`
Expected: PASS

- [ ] **Step 5: Write the conditional-registration test**

The spec requires proving the controller is only reachable when *both* gating flags are on (not just unit-testing its method in isolation, which Steps 1-4 already cover). This uses Spring's `ApplicationContextRunner` to evaluate the real `@ConditionalOnProperty` against a real (minimal) context — same tool `MfaConfigTest`/`OAuthConfigTest` already use for bean-presence assertions elsewhere in this codebase, applied directly to a `@Conditional`-annotated component instead of a `@Configuration` class's `@Bean` methods:

```java
package com.example.authsvc.api.controller;

import com.example.authsvc.application.service.ImpersonationTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class V1InternalTokenControllerConditionalRegistrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ImpersonationTokenService.class, () -> mock(ImpersonationTokenService.class))
            .withUserConfiguration(V1InternalTokenController.class);

    @Test
    void bothFlagsOn_controllerRegistered() {
        contextRunner
                .withPropertyValues("app.super-admin.enabled=true", "app.internal-hmac-auth.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(V1InternalTokenController.class));
    }

    @Test
    void superAdminOff_controllerAbsent() {
        contextRunner
                .withPropertyValues("app.super-admin.enabled=false", "app.internal-hmac-auth.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(V1InternalTokenController.class));
    }

    @Test
    void internalHmacAuthOff_controllerAbsent() {
        contextRunner
                .withPropertyValues("app.super-admin.enabled=true", "app.internal-hmac-auth.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(V1InternalTokenController.class));
    }
}
```

- [ ] **Step 6: Run this test to verify it passes**

Run: `./gradlew :gen-auth-starter:test --tests "com.example.authsvc.api.controller.V1InternalTokenControllerConditionalRegistrationTest"`
Expected: PASS (3/3) — this is the test that actually proves the `@ConditionalOnProperty` array requires *both* flags, not either.

- [ ] **Step 7: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/api/controller/V1InternalTokenController.java gen-auth-starter/src/test/java/com/example/authsvc/api/controller/V1InternalTokenControllerTest.java gen-auth-starter/src/test/java/com/example/authsvc/api/controller/V1InternalTokenControllerConditionalRegistrationTest.java
git commit -m "add V1InternalTokenController exposing /v1/impersonation-token for HMAC-signing callers"
```

---

### Task 3: Wire `InternalHmacAuthFilter` into `SecurityConfig`

**Files:**
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java`

**Interfaces:**
- Consumes: `InternalHmacAuthFilter` (Task 1), `V1InternalTokenController`'s path `/v1/impersonation-token` (Task 2, referenced only as a string in the permitAll matcher — the controller class itself is never imported into `SecurityConfig`).
- Produces: nothing new for later tasks — this is the final integration point for this sub-project.

`SecurityConfig`'s current constructor (as of this plan's writing) is:

```java
public SecurityConfig(
        JwtAuthenticationFilter jwtFilter,
        InternalTokenAuthFilter internalTokenAuthFilter,
        JwtAuthEntryPoint authEntryPoint,
        JwtAccessDeniedHandler accessDeniedHandler,
        CorsProperties corsProperties,
        @Autowired(required = false) OAuth2AuthorizationRequestResolver oAuth2AuthorizationRequestResolver,
        @Autowired(required = false) AuthorizationRequestRepository<OAuth2AuthorizationRequest> oAuth2AuthorizationRequestRepository,
        @Autowired(required = false) OAuthLoginSuccessHandler oAuthLoginSuccessHandler,
        @Autowired(required = false) OAuthLoginFailureHandler oAuthLoginFailureHandler) {
```

`InternalHmacAuthFilter` is always registered (Task 1's global constraint — it's a required, non-optional constructor parameter here, exactly like `internalTokenAuthFilter` and `jwtFilter`), so add it as a plain required parameter, not `@Autowired(required = false)`.

- [ ] **Step 1: Add the import and field**

Add this import alongside the existing filter/handler imports:

```java
import com.example.authsvc.infrastructure.security.filter.InternalHmacAuthFilter;
```

Add this field, directly after the existing `internalTokenAuthFilter` field declaration:

```java
    private final InternalHmacAuthFilter   internalHmacAuthFilter;
```

- [ ] **Step 2: Add the constructor parameter and assignment**

In the constructor, add `InternalHmacAuthFilter internalHmacAuthFilter` as a parameter directly after `InternalTokenAuthFilter internalTokenAuthFilter` (before `JwtAuthEntryPoint authEntryPoint`), and add `this.internalHmacAuthFilter = internalHmacAuthFilter;` directly after the existing `this.internalTokenAuthFilter = internalTokenAuthFilter;` assignment.

- [ ] **Step 3: Add the permitAll entry**

In `filterChain(...)`'s `authorizeHttpRequests` block, add `/v1/impersonation-token` to the existing `POST` permitAll list (the same list `/api/v1/auth/oauth/complete-signup` is already the last entry of):

```java
                                "/api/v1/auth/oauth/complete-signup",
                                // HMAC-signature-authenticated, not JWT-authenticated — see
                                // InternalHmacAuthFilter. Reachable only when both
                                // app.super-admin.enabled and app.internal-hmac-auth.enabled
                                // are true (V1InternalTokenController's own gate); 404s otherwise.
                                "/v1/impersonation-token"
                        ).permitAll()
```

(This replaces the existing block's closing `"/api/v1/auth/oauth/complete-signup"` line — that line loses its own `).permitAll()` and gains the two new lines above instead, in the same `.requestMatchers(HttpMethod.POST, ...)` call.)

- [ ] **Step 4: Register the filter in the chain**

Change:

```java
                .addFilterBefore(internalTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter,               UsernamePasswordAuthenticationFilter.class);
```

to:

```java
                .addFilterBefore(internalTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalHmacAuthFilter,  UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter,               UsernamePasswordAuthenticationFilter.class);
```

- [ ] **Step 5: Add the `FilterRegistrationBean` that suppresses double-registration**

Every filter added via `addFilterBefore(...)` in this class also needs its generic servlet-container auto-registration disabled — see the existing `jwtFilterRegistration()`/`internalTokenFilterRegistration()` beans; without this, Spring Boot's `@Component`-triggered `FilterRegistrationBean` would run the filter a second time outside the security chain. Add, directly after `internalTokenFilterRegistration()`:

```java
    @Bean
    public FilterRegistrationBean<InternalHmacAuthFilter> internalHmacAuthFilterRegistration() {
        FilterRegistrationBean<InternalHmacAuthFilter> reg = new FilterRegistrationBean<>(internalHmacAuthFilter);
        reg.setEnabled(false);
        return reg;
    }
```

- [ ] **Step 6: Compile and run the full test suite**

Run: `./gradlew :gen-auth-starter:test`
Expected: BUILD SUCCESSFUL, all tests pass (existing suite plus Tasks 1-2's new tests)

- [ ] **Step 7: Commit**

```bash
git add gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java
git commit -m "wire InternalHmacAuthFilter and /v1/impersonation-token into SecurityConfig"
```

---

### Task 4: Demo app config

**Files:**
- Modify: `gen-auth-demo/src/main/resources/application.yaml`

**Interfaces:** none — configuration/documentation only.

- [ ] **Step 1: Add the demo config block**

In `gen-auth-demo/src/main/resources/application.yaml`, find the existing `oauth:` block under the top-level `app:` key (added by the OAuth work — search for `OAUTH2 CLIENT CONFIG` to locate it) and add a new `internal-hmac-auth:` block directly after it, at the same indentation level as `oauth:`, following this file's established per-feature comment-header style:

```yaml
  # ===================================================================
  # INTERNAL HMAC AUTH (optional — off by default)
  # ===================================================================
  # A second internal-caller auth mechanism alongside app.internal-secret's
  # plain header check — verifies HMAC-SHA256-signed requests instead.
  # Matches CPMS-Platform's auth-svc wire format exactly (X-CPMS-Timestamp/
  # X-CPMS-Signature headers, same internal-service-secret). To try this
  # locally: flip the flag, list the paths to guard, then sign a request the
  # same way CPMS-Platform's sup-svc/src/infra/external/auth-svc.client.ts does
  # (HMAC-SHA256 over "{unix-timestamp}:{raw-body}").
  internal-hmac-auth:
    enabled: false
    target-paths:
      - /v1/impersonation-token
```

- [ ] **Step 2: Verify the YAML is valid and the build still succeeds**

Run: `./gradlew :gen-auth-starter:test`
Expected: BUILD SUCCESSFUL (this file doesn't affect `gen-auth-starter`'s own build, but a syntax error would still be worth catching now)

- [ ] **Step 3: Commit**

```bash
git add gen-auth-demo/src/main/resources/application.yaml
git commit -m "add example app.internal-hmac-auth config to demo app"
```
