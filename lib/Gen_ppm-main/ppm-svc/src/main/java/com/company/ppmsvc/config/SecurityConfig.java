package com.company.ppmsvc.config;

import com.company.ppmsvc.infrastructure.security.JtiRevocationFilter;
import com.company.ppmsvc.infrastructure.security.PpmAccessAuthorizationFilter;
import com.company.ppmsvc.infrastructure.security.PpmAdminAuthorizationFilter;
import com.company.ppmsvc.infrastructure.security.CpmsJwtAuthConverter;
import com.company.ppmsvc.infrastructure.security.CpmsSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * PPM-SVC Security configuration.
 *
 * <p>Authentication: OAuth2 JWT resource server validating tokens issued by AUTH-SVC.
 * Tokens are RS256-signed; public keys are fetched from AUTH-SVC's JWKS endpoint.
 *
 * <p>Authorization filter chain (in execution order after JWT validation):
 * <ol>
 *   <li>{@link JtiRevocationFilter} — rejects tokens whose JTI appears in
 *       {@code auth:revoked:{jti}} in Redis; fail-open on Redis outage.</li>
 *   <li>{@link PpmAccessAuthorizationFilter} — passes public paths through;
 *       allows SUPER_ADMIN unconditionally; requires {@code ppm.read} for all
 *       other authenticated requests.</li>
 *   <li>{@link PpmAdminAuthorizationFilter} — for write methods (POST/PUT/PATCH/
 *       DELETE) on non-public paths, rejects unless caller is SUPER_ADMIN.</li>
 * </ol>
 *
 * <p>PPM-SVC is a platform-level catalog with no per-tenant rows and requires
 * no cross-tenant path validation, so no tenant-scope filter is registered.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties(CpmsSecurityProperties.class)
public class SecurityConfig {

    private final JtiRevocationFilter       jtiRevocationFilter;
    private final PpmAccessAuthorizationFilter ppmAccessFilter;
    private final PpmAdminAuthorizationFilter  ppmAdminFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/**",
                    "/v1/docs", "/v1/docs/**",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v1/swagger-ui/**",
                    // Public catalog endpoints — no token required
                    "/api/v1/ppm/plans",
                    "/api/v1/ppm/plans/*",
                    "/api/v1/ppm/plans/code/*",
                    "/api/v1/ppm/plans/slug/*",
                    "/api/v1/ppm/prices/resolve",
                    "/api/v1/ppm/promo-codes/validate",
                    // Public for BSM version-locking during checkout (C2) — read-only, no writes
                    "/api/v1/ppm/plans/*/versions/latest",
                    // Public for BSM C4 add-on price resolution — read-only, no writes
                    "/api/v1/ppm/add-ons/*/prices/active",
                    // Public for BSM event publisher and drift detection (PPM-12A)
                    "/api/v1/ppm/plan-versions/*/meta",
                    // Public for BSM downgrade preflight (PPM-12B)
                    "/api/v1/ppm/plan-versions/*/limits",
                    // Public for USG-SVC limit seeding at subscription time (no JWT issued to internal workers)
                    "/api/v1/ppm/plans/*/entitlements/resolved",
                    // Public for REG-SVC signup pricing page feature list
                    "/api/v1/ppm/plans/*/modules"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder)
                    .jwtAuthenticationConverter(new CpmsJwtAuthConverter())
                )
            )
            // Filter order: revocation → access gate → admin write gate
            .addFilterAfter(jtiRevocationFilter,  BearerTokenAuthenticationFilter.class)
            .addFilterAfter(ppmAccessFilter,       JtiRevocationFilter.class)
            .addFilterAfter(ppmAdminFilter,        PpmAccessAuthorizationFilter.class);

        return http.build();
    }

    /**
     * Prevents Spring Boot from registering PPM security filters as top-level Servlet
     * filters. They must ONLY run inside the Spring Security chain.
     */
    @Bean
    public FilterRegistrationBean<JtiRevocationFilter> jtiRevocationFilterRegistration(
            JtiRevocationFilter filter) {
        FilterRegistrationBean<JtiRevocationFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<PpmAccessAuthorizationFilter> ppmAccessFilterRegistration(
            PpmAccessAuthorizationFilter filter) {
        FilterRegistrationBean<PpmAccessAuthorizationFilter> bean =
            new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<PpmAdminAuthorizationFilter> ppmAdminFilterRegistration(
            PpmAdminAuthorizationFilter filter) {
        FilterRegistrationBean<PpmAdminAuthorizationFilter> bean =
            new FilterRegistrationBean<>(filter);
        bean.setEnabled(false);
        return bean;
    }

    @Bean
    public JwtDecoder jwtDecoder(CpmsSecurityProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri(props.getJwksUri())
            .build();

        OAuth2TokenValidator<Jwt> issuerValidator =
            JwtValidators.createDefaultWithIssuer(props.getIssuer());
        OAuth2TokenValidator<Jwt> combined =
            new DelegatingOAuth2TokenValidator<>(issuerValidator);
        decoder.setJwtValidator(combined);
        return decoder;
    }
}
