package com.company.ppmsvc.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("PPM-SVC — Plan and Pricing Management")
                .description("""
                    Plan and Pricing Management Service — CPMS Platform.

                    **Authentication:** All endpoints require a valid JWT obtained from AUTH-SVC.
                    Click **Authorize**, paste your token in the `bearerAuth` field.

                    Obtain a token:
                    - `POST /auth/login` on AUTH-SVC (port 8101)
                    - Use the returned `accessToken` value here.
                    """)
                .version("1.0.0")
                .contact(new Contact().name("CPMS Team").email("team@metaupspace.com")))
            // Explicit relative "/" — every @RequestMapping here already embeds
            // the full /api/v1/ppm/... prefix, so springdoc's default
            // auto-detected server URL would double it in "Try it out" requests.
            .servers(List.of(new Server().url("/")))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste your JWT access token (without the 'Bearer ' prefix)")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
