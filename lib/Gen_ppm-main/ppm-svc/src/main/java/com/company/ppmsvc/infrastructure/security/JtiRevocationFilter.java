package com.company.ppmsvc.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Checks whether the JWT's {@code jti} claim has been revoked.
 *
 * <p>AUTH-SVC writes revoked JTIs to {@code auth:revoked:{jti}} in Redis (Valkey).
 * This filter rejects any request whose token appears in that keyspace.
 *
 * <p>Runs inside the Security chain immediately after {@link
 * org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter}
 * so the JTI is already available on the principal.
 *
 * <p>Fail-open: if Redis is unavailable the check is skipped and the request
 * continues — same pattern used by SUP-SVC. An already-short JWT TTL (15 min)
 * limits the exposure window.
 *
 * <p>Unauthenticated requests (public paths with no bearer token) have no
 * principal and are passed through without any Redis call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JtiRevocationFilter extends OncePerRequestFilter {

    private static final String REVOKED_KEY_PREFIX = "auth:revoked:";
    private static final String REVOKED_BODY =
        "{\"error\":\"Unauthorized\",\"message\":\"Token has been revoked\"}";

    private final StringRedisTemplate redisTemplate;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator")
            || path.startsWith("/v1/docs")
            || path.startsWith("/v1/swagger-ui")
            || path.startsWith("/v3/api-docs")
            || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CpmsAuthenticatedPrincipal principal)) {
            // No authenticated principal — anonymous / public path, pass through.
            chain.doFilter(request, response);
            return;
        }

        String jti = principal.jti();
        if (jti == null || jti.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        if (isRevoked(jti)) {
            log.warn("ppm.jti.revoked jti={} userId={} path={}",
                jti, principal.userId(), request.getServletPath());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(REVOKED_BODY);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRevoked(String jti) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(REVOKED_KEY_PREFIX + jti));
        } catch (Exception e) {
            log.warn("ppm.jti.revocation_check.redis_unavailable jti={} — fail-open: {}",
                jti, e.getMessage());
            return false;
        }
    }
}
