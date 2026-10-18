package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the magic-link / forgot-password flow.
 * Only read when {@code app.magic-link.enabled=true}.
 *
 * <pre>
 * auth:
 *   magic-link:
 *     ttl: 15m
 *     frontend-reset-url: https://app.example.com/reset-password
 *     rate-limit-max-requests: 3
 *     rate-limit-window: 1h
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "auth.magic-link")
public class MagicLinkProperties {

    /** How long a magic-link token stays valid in Redis. */
    private Duration ttl = Duration.ofMinutes(15);

    /** Frontend URL that accepts ?token=<rawToken>. */
    private String frontendResetUrl = "http://localhost:3000/reset-password";

    /** Maximum issue attempts per (IP + email) within the rate-limit window. */
    private int rateLimitMaxRequests = 3;

    /** Sliding window for rate-limit counters. */
    private Duration rateLimitWindow = Duration.ofHours(1);
}
