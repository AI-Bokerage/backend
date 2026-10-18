package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "auth")
public class AuthBehaviorProperties {

    /** "open" (default) or "disabled" — gates POST /api/v1/auth/register. */
    private String registrationMode = "open";

    /** "cookie" (default, existing behavior) or "json" — response body includes raw tokens instead of Set-Cookie headers. */
    private String tokenDeliveryMode = "cookie";

    public boolean isRegistrationOpen() {
        return "open".equalsIgnoreCase(registrationMode);
    }

    public boolean isJsonTokenDelivery() {
        return "json".equalsIgnoreCase(tokenDeliveryMode);
    }
}
