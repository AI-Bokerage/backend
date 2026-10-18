package com.example.admsvc.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Gen_ADM performs its own authorization via {@code PermissionChecker}
 * (populated from {@code GenAdmPrincipal} in {@code SecurityContextHolder},
 * which the host app is responsible for setting) — this permits every
 * request at the Spring Security layer so its default Basic Auth
 * auto-configuration never engages. Authorization enforcement is
 * exclusively {@code PermissionChecker.require(...)}'s job.
 *
 * <p>Scoped via {@code securityMatcher} to Gen_ADM's own API routes only, so
 * this chain can never match — and therefore never permit-all — routes
 * belonging to the rest of the host application. {@code
 * @ConditionalOnMissingBean(SecurityFilterChain.class)} backs this bean off
 * entirely if the host app already defines its own {@code
 * SecurityFilterChain}, so the host's own security config always wins
 * deterministically instead of an ambiguous multi-chain situation.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain genAdmSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/roles/**", "/api/v1/permissions/**", "/api/v1/assignments/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
