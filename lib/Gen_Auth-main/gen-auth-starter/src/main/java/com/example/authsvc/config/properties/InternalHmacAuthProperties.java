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
