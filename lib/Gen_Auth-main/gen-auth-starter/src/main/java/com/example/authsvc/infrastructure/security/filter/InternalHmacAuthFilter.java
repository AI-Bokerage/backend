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
