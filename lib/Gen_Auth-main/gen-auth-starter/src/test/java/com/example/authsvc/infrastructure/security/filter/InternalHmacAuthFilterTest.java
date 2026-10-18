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
        request.setServletPath("/v1/impersonation-token");
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
        request.setServletPath("/v1/impersonation-token");
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
        request.setServletPath("/v1/impersonation-token");
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
        request.setServletPath("/v1/impersonation-token");
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
        request.setServletPath("/v1/client-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void disabled_skipsVerificationEntirely() throws Exception {
        properties.setEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/impersonation-token");
        request.setServletPath("/v1/impersonation-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void emptyBody_signsAgainstLiteralEmptyObjectString() throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String signature = signLikeRealCaller(timestamp, "{}", SECRET);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/impersonation-token");
        request.setServletPath("/v1/impersonation-token");
        request.addHeader("X-CPMS-Timestamp", timestamp);
        request.addHeader("X-CPMS-Signature", signature);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(ArgumentMatchers.any(), ArgumentMatchers.eq(response));
    }
}
