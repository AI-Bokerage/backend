package com.example.tnt_svc.config.openapi;

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
public class GenTntOpenApiConfig {

    @Value("${server.port:8201}")
    private int serverPort;

    @Bean
    public OpenAPI genTntOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Gen_TNT — Tenant Provisioning API")
                        .version("v1")
                        .description(
                                "Generalized tenant-provisioning service. Tenant CRUD (create/suspend/reactivate/" +
                                "cancel/purge) backed by a webhook-driven, configuration-defined provisioning saga: " +
                                "a sequential list of sync/async steps, per-tenant Redis locking, retry-primary " +
                                "recovery, and timeout-triggered best-effort compensation. Embed gen-tnt-starter " +
                                "directly in a Spring Boot app, or call gen-tnt-demo over HTTP as a standalone " +
                                "service — see docs/integration-guide.md.")
                        .contact(new Contact().name("Gen_MS Platform Team")))
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local")))
                .components(new Components()
                        .addSecuritySchemes("internalSecret", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Secret")
                                .description("Shared secret gating every /api/v1/** and /internal/** call (gentnt.internal-secret)")));
    }
}
