package com.example.authsvc.infrastructure.security.filter;

import com.example.authsvc.api.controller.V1InternalTokenController;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.application.service.ImpersonationTokenService;
import com.example.authsvc.config.properties.InternalHmacAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Wires the real {@link InternalHmacAuthFilter} in front of the real
 * {@link V1InternalTokenController} through Spring's standalone MockMvc —
 * the unit tests for each class in isolation don't prove a genuinely signed
 * HTTP request actually flows through the filter's body-caching wrapper into
 * controller JSON deserialization; this does.
 */
@ExtendWith(MockitoExtension.class)
class InternalHmacAuthFilterIntegrationTest {

    private static final String SECRET = "integration-test-secret";

    @Mock private ImpersonationTokenService impersonationTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InternalHmacAuthProperties properties = new InternalHmacAuthProperties();
        properties.setEnabled(true);
        properties.setTargetPaths(List.of("/v1/impersonation-token"));

        InternalHmacAuthFilter filter = new InternalHmacAuthFilter(properties);
        ReflectionTestUtils.setField(filter, "secret", SECRET);

        V1InternalTokenController controller =
                new V1InternalTokenController(impersonationTokenService, null, properties);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(filter)
                .build();
    }

    private static String sign(String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal((timestamp + ":" + body).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(raw);
    }

    @Test
    void validlySignedRequest_reachesControllerAndReturns200() throws Exception {
        String body = "{\"superAdminId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"tenantId\":\"22222222-2222-2222-2222-222222222222\","
                + "\"impersonationRoleId\":\"33333333-3333-3333-3333-333333333333\","
                + "\"sessionId\":\"session-abc\",\"writeConsent\":false}";
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String signature = sign(timestamp, body);

        when(impersonationTokenService.issue(any())).thenReturn(
                new ImpersonationTokenResponse("jwt-value", 3600, "session-abc"));

        mockMvc.perform(post("/v1/impersonation-token")
                        .servletPath("/v1/impersonation-token")
                        .contentType("application/json")
                        .header("X-CPMS-Timestamp", timestamp)
                        .header("X-CPMS-Signature", signature)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void unsignedRequest_neverReachesController() throws Exception {
        mockMvc.perform(post("/v1/impersonation-token")
                        .servletPath("/v1/impersonation-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
