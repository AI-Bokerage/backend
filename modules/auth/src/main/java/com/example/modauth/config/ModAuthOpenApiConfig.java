package com.example.modauth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds a "bearerAuth" security scheme (Authorization: Bearer &lt;token&gt;) via
 * springdoc's {@link GlobalOpenApiCustomizer} extension point, additively —
 * gen-auth-starter's own {@code OpenApiConfig} already provides the base
 * {@code OpenAPI} bean (title, servers, cookieAuth/internalSecret schemes);
 * defining a second {@code @Bean OpenAPI} here would conflict with it, so
 * this customizer merges into that same bean instead. Used by the endpoints
 * this module adds, which — like the starter's own — accept the JWT as
 * either a cookie or an Authorization header.
 */
@Configuration
public class ModAuthOpenApiConfig {

    private static final String GUIDE = """
            ## Using this API with the CRM
            1. **Sign in**: `POST /api/v1/modauth/login` (Tenant Admin, Team Lead, Broker, Assistant) or
               `POST /api/v1/auth/super-admin/login`. By default the token is set as an `access_token`
               cookie for localhost — the CRM/Platform Swagger (port 4000, `/docs`) in the same browser
               then uses it automatically. Run with `AUTH_TOKENDELIVERYMODE=json` to get `accessToken`
               in the response body instead, then **Authorize**.
            2. The same token works on the CRM/Platform API.
            3. **Sessions**: access tokens last 15 minutes; refresh with `POST /api/v1/auth/refresh`.
               A refresh more than `SESSION_IDLE_TIMEOUT_MINUTES` (30) after the previous one signs the
               session out (inactivity); every session ends after 7 days. Refresh only on user activity.
            4. **Passwords**: at least 12 characters, and not your current or last 5 passwords
               (accept invitation, change password, forgot-password reset).
            5. **Blocked sign-in**: 5 failed tries lock the account for 15 minutes (warning email sent);
               a suspended or cancelled brokerage gets 403 at login and on refresh.
            6. **My profile**: `GET/PATCH /api/v1/modauth/me` (name, phone).
            """;

    @Bean
    public GlobalOpenApiCustomizer bearerAuthSchemeCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste the accessToken from /api/v1/modauth/login (json delivery mode) here"));
            if (openApi.getInfo() != null) {
                String base = openApi.getInfo().getDescription() == null ? "" : openApi.getInfo().getDescription() + "\n\n";
                openApi.getInfo().setDescription(base + GUIDE);
            }
        };
    }
}
