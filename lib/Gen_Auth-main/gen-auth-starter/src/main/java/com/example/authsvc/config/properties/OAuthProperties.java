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
