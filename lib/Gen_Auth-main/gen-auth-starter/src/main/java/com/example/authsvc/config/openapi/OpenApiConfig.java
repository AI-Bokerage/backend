package com.example.authsvc.config.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8101}")
    private int serverPort;

    @Bean
    public OpenAPI authServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Auth Service API")
                        .version("v1")
                        .description(
                                "Standalone, generalized auth service. Handles registration, login, refresh-token " +
                                "rotation with replay detection, logout, change-password, session lookup, and JWKS " +
                                "key distribution — including runtime multi-key rotation for jwt.signing-mode=local " +
                                "deployments (rotate/retire via /internal/auth/keys, no restart required). JWTs are " +
                                "RS256-signed, delivered as HttpOnly cookies or JSON depending on " +
                                "auth.token-delivery-mode. Magic-link reset, super-admin/impersonation, MFA, and " +
                                "OAuth/SSO are not implemented yet.")
                        .contact(new Contact().name("CPMS Platform Team")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local"),
                        new Server().url("https://api.cpms.com").description("Production")))
                .components(new Components()
                        .addSecuritySchemes("cookieAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("access_token")
                                .description("HttpOnly JWT access token set via Set-Cookie header on successful login"))
                        .addSecuritySchemes("internalSecret", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Secret")
                                .description("Shared secret for service-to-service /internal/** calls (INTERNAL_SERVICE_SECRET)")));
    }
}
