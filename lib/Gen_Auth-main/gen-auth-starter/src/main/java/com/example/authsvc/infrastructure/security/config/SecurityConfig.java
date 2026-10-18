package com.example.authsvc.infrastructure.security.config;

import com.example.authsvc.config.properties.CorsProperties;
import com.example.authsvc.infrastructure.security.handler.JwtAccessDeniedHandler;
import com.example.authsvc.infrastructure.security.handler.JwtAuthEntryPoint;
import com.example.authsvc.infrastructure.security.filter.InternalHmacAuthFilter;
import com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter;
import com.example.authsvc.infrastructure.security.filter.JwtAuthenticationFilter;
import com.example.authsvc.infrastructure.security.oauth.OAuthLoginFailureHandler;
import com.example.authsvc.infrastructure.security.oauth.OAuthLoginSuccessHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter  jwtFilter;
    private final InternalTokenAuthFilter  internalTokenAuthFilter;
    private final InternalHmacAuthFilter   internalHmacAuthFilter;
    private final JwtAuthEntryPoint        authEntryPoint;
    private final JwtAccessDeniedHandler   accessDeniedHandler;
    private final CorsProperties           corsProperties;

    /** Non-null only when app.oauth.enabled=true AND a provider is configured — see OAuthConfig. */
    private final OAuth2AuthorizationRequestResolver oAuth2AuthorizationRequestResolver;
    private final AuthorizationRequestRepository<OAuth2AuthorizationRequest> oAuth2AuthorizationRequestRepository;
    private final OAuthLoginSuccessHandler oAuthLoginSuccessHandler;
    private final OAuthLoginFailureHandler oAuthLoginFailureHandler;

    public SecurityConfig(
            JwtAuthenticationFilter jwtFilter,
            InternalTokenAuthFilter internalTokenAuthFilter,
            InternalHmacAuthFilter internalHmacAuthFilter,
            JwtAuthEntryPoint authEntryPoint,
            JwtAccessDeniedHandler accessDeniedHandler,
            CorsProperties corsProperties,
            @Autowired(required = false) OAuth2AuthorizationRequestResolver oAuth2AuthorizationRequestResolver,
            @Autowired(required = false) AuthorizationRequestRepository<OAuth2AuthorizationRequest> oAuth2AuthorizationRequestRepository,
            @Autowired(required = false) OAuthLoginSuccessHandler oAuthLoginSuccessHandler,
            @Autowired(required = false) OAuthLoginFailureHandler oAuthLoginFailureHandler) {
        this.jwtFilter = jwtFilter;
        this.internalTokenAuthFilter = internalTokenAuthFilter;
        this.internalHmacAuthFilter = internalHmacAuthFilter;
        this.authEntryPoint = authEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsProperties = corsProperties;
        this.oAuth2AuthorizationRequestResolver = oAuth2AuthorizationRequestResolver;
        this.oAuth2AuthorizationRequestRepository = oAuth2AuthorizationRequestRepository;
        this.oAuthLoginSuccessHandler = oAuthLoginSuccessHandler;
        this.oAuthLoginFailureHandler = oAuthLoginFailureHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ── Truly public auth endpoints — no JWT needed ──────────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/register",
                                "/api/v1/auth/magic-link/**",
                                "/api/v1/auth/super-admin/**",
                                // Challenge-token-authenticated, not JWT-authenticated: the
                                // caller has no session yet (that's the whole point of the
                                // MFA challenge step), so this must be reachable pre-JWT.
                                "/api/v1/auth/mfa/verify-login",
                                // Also challenge-token-authenticated for the tenant-required-
                                // but-not-yet-enrolled login path (see MfaController.enroll):
                                // that user's /login only ever returns a challenge, never a
                                // JWT, so they can never reach this endpoint via a session.
                                // Still works normally via JWT for a logged-in user's
                                // voluntary self-enrollment — the controller resolves the
                                // caller from the JWT principal when one is present.
                                "/api/v1/auth/mfa/enroll",
                                // Setup-token-authenticated, not JWT-authenticated — the
                                // caller has no session yet (a brand-new OAuth signup is
                                // blocked pending a password, same category as the MFA
                                // challenge-token endpoints above).
                                "/api/v1/auth/oauth/complete-signup",
                                // HMAC-signature-authenticated, not JWT-authenticated — see
                                // InternalHmacAuthFilter. Each reachable only when
                                // app.internal-hmac-auth.enabled=true AND its own feature flag
                                // (app.super-admin.enabled / app.client-token.enabled) is true;
                                // 404s otherwise (V1InternalTokenController's per-handler gate).
                                "/v1/impersonation-token",
                                "/v1/client-token"
                        ).permitAll()
                        // ── Spring Security's own OAuth2 login endpoints ──────────────
                        .requestMatchers(HttpMethod.GET,
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**"
                        ).permitAll()
                        // ── Internal service-to-service endpoints — secret-header protected ──
                        .requestMatchers("/internal/**").permitAll()
                        // ── JWKS, actuator, docs — always public ─────────────────────
                        .requestMatchers(
                                "/.well-known/jwks.json",
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/v1/docs", "/v1/docs/**",
                                "/swagger-ui/**",
                                "/v1/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**"
                        ).permitAll()
                        // ── Protected auth endpoints — JWT required ──────────────────
                        .requestMatchers(HttpMethod.GET,  "/api/v1/auth/session").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // internalTokenAuthFilter guards /internal/** with shared-secret header
                // Runs before JWT so internal callers don't need user tokens
                .addFilterBefore(internalTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalHmacAuthFilter,  UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter,               UsernamePasswordAuthenticationFilter.class);

        // Gate on an OAuthConfig-owned bean, not clientRegistrationRepository directly:
        // Boot registers ClientRegistrationRepository off spring.security.oauth2.client.
        // registration.* alone, independent of app.oauth.enabled, so a provider configured
        // with the flag left off would leave this non-null while OAuthConfig's four beans
        // stay null. oAuth2AuthorizationRequestResolver is only ever produced by OAuthConfig,
        // and its bean method itself requires ClientRegistrationRepository as an argument —
        // so non-null here guarantees all five OAuth beans (including the repository) exist.
        if (oAuth2AuthorizationRequestResolver != null) {
            http.oauth2Login(oauth -> oauth
                    .authorizationEndpoint(a -> a
                            .authorizationRequestResolver(oAuth2AuthorizationRequestResolver)
                            .authorizationRequestRepository(oAuth2AuthorizationRequestRepository))
                    .successHandler(oAuthLoginSuccessHandler)
                    .failureHandler(oAuthLoginFailureHandler));
            log.info("oauth2.login.enabled");
        }

        SecurityFilterChain chain = http.build();
        log.info("security.initialized stateless=true cors_origins={}", corsProperties.getAllowedOrigins());
        return chain;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(corsProperties.getAllowedMethods());
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());
        config.setAllowCredentials(corsProperties.isAllowCredentials());
        config.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        log.debug("cors.configured origins={} methods={}", corsProperties.getAllowedOrigins(), corsProperties.getAllowedMethods());
        return source;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(jwtFilter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<InternalTokenAuthFilter> internalTokenFilterRegistration() {
        FilterRegistrationBean<InternalTokenAuthFilter> reg = new FilterRegistrationBean<>(internalTokenAuthFilter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<InternalHmacAuthFilter> internalHmacAuthFilterRegistration() {
        FilterRegistrationBean<InternalHmacAuthFilter> reg = new FilterRegistrationBean<>(internalHmacAuthFilter);
        reg.setEnabled(false);
        return reg;
    }
}
