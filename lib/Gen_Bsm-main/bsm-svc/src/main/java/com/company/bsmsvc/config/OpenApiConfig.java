package com.company.bsmsvc.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bsmOpenApi() {
        final String schemeName = "bearerAuth";
        return new OpenAPI()
            .info(new Info()
                .title("BSM-SVC — Billing & Subscription Management")
                .version("1.0.0")
                .description("Billing engine for the CPMS platform. " +
                    "Paste a JWT from AUTH-SVC into the Authorize dialog below."))
            // Explicit relative "/" — every @RequestMapping here already embeds
            // the full /api/v1/bsm/... prefix, so springdoc's default
            // auto-detected server URL (.../api/v1/bsm, from X-Forwarded-Prefix
            // set for docs-asset routing) would double it in "Try it out"
            // requests: /api/v1/bsm/api/v1/bsm/...
            .servers(List.of(new Server().url("/")))
            .addSecurityItem(new SecurityRequirement().addList(schemeName))
            .components(new Components()
                .addSecuritySchemes(schemeName, new SecurityScheme()
                    .name(schemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT issued by AUTH-SVC. " +
                        "Login at POST http://localhost:8101/api/v1/auth/login and paste the accessToken here.")));
    }
}
