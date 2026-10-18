package com.example.modauth.config;

import com.example.authsvc.infrastructure.security.filter.JwtAuthenticationFilter;
import com.example.authsvc.infrastructure.security.handler.JwtAccessDeniedHandler;
import com.example.authsvc.infrastructure.security.handler.JwtAuthEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * A second, narrowly-scoped {@link SecurityFilterChain} matched only against
 * {@code /api/v1/modauth/**} (Spring Security picks the first chain whose
 * {@code securityMatcher} matches a request). This is additive — it doesn't
 * touch gen-auth-starter's own {@code SecurityConfig}/filter chain, which
 * still governs every other path exactly as before.
 *
 * <p>Reuses the starter's own {@code JwtAuthenticationFilter} bean so
 * {@code @AuthenticationPrincipal AuthenticatedUser} works the same way here
 * as it does in the starter's own controllers.
 */
@Configuration
@Order(1)
@RequiredArgsConstructor
public class ModAuthSecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final JwtAuthEntryPoint authEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain modAuthFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/modauth/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: no session yet (login itself, and the two
                        // invitation-accept steps reached straight from an email link).
                        .requestMatchers(HttpMethod.POST, "/api/v1/modauth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/modauth/invitations/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/modauth/invitations/*/accept").permitAll()
                        // Everything else under this prefix (create/resend an invitation) needs a JWT.
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
