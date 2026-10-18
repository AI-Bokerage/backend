package com.company.bsmsvc.config;

import io.cpms.common.security.CpmsJwtAuthConverter;
import io.cpms.common.security.CpmsSecurityAutoConfiguration;
import io.cpms.common.security.TenantScopeFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Import(CpmsSecurityAutoConfiguration.class)
public class SecurityConfig {

    /**
     * Paths that bypass JWT validation entirely.
     *
     * <p>Webhook paths are protected by provider HMAC signature verification
     * inside {@code WebhookProcessingService} — no JWT is issued by a payment
     * provider, so these endpoints must be permit-all at the security layer.</p>
     */
    private static final String[] PUBLIC_PATHS = {
        "/v1/docs",
        "/v1/docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v1/swagger-ui/**",
        "/v3/api-docs/**",
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/actuator/prometheus",
        "/api/v1/bsm/webhooks/stripe",
        "/api/v1/bsm/webhooks/razorpay"
    };

    @Bean
    public BsmInternalSecretFilter bsmInternalSecretFilter(BsmSecurityProperties props) {
        return new BsmInternalSecretFilter(props.internalSecret());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtDecoder jwtDecoder,
                                           CpmsJwtAuthConverter jwtAuthConverter,
                                           TenantScopeFilter tenantScopeFilter,
                                           BsmInternalSecretFilter bsmInternalSecretFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(PUBLIC_PATHS).permitAll()
                // /internal/** is guarded by BsmInternalSecretFilter, not JWT
                .requestMatchers("/internal/**").permitAll()
                // Lock down all other actuator endpoints not in PUBLIC_PATHS
                .requestMatchers("/actuator/**").denyAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(jwtAuthConverter)
                )
            )
            // Internal filter runs first: validates /internal/** before JWT processing starts
            .addFilterBefore(bsmInternalSecretFilter, UsernamePasswordAuthenticationFilter.class)
            // Tenant scope filter runs after JWT is validated and principal is in SecurityContext
            .addFilterAfter(tenantScopeFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
