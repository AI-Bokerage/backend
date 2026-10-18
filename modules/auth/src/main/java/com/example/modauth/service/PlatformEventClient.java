package com.example.modauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Flow 1 "The activity log records logins … and security events": sign-in
 * and security events happen here, but the Super Admin's activity log lives
 * in modules/platform — so each one is forwarded there, fire-and-forget.
 * Blank PLATFORM_EVENTS_URL (modules/platform not deployed) skips it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformEventClient {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final ObjectMapper objectMapper;

    @Value("${modauth.platform-events-url:}")
    private String platformEventsUrl;

    @Value("${internal-service-secret}")
    private String internalServiceSecret;

    public void send(String action, UUID tenantId, UUID userId, String ip, String details) {
        if (platformEventsUrl == null || platformEventsUrl.isBlank()) return;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("action", action);
            body.put("tenantId", tenantId);
            body.put("userId", userId);
            body.put("ip", ip);
            body.put("details", details);
            HttpRequest request = HttpRequest.newBuilder(URI.create(platformEventsUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Secret", internalServiceSecret)
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HTTP_CLIENT.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.discarding())
                    .whenComplete((res, err) -> {
                        if (err != null || res.statusCode() >= 300) {
                            log.warn("platform.event_forward_failed action={} status={}",
                                    action, res == null ? null : res.statusCode(), err);
                        }
                    });
        } catch (Exception e) {
            // Never let audit forwarding break a login.
            log.warn("platform.event_forward_failed action={}", action, e);
        }
    }
}
