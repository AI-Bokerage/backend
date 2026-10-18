package com.example.authsvc.infrastructure.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Guards {@code /internal/**} endpoints with a shared-secret header check.
 *
 * <p>Callers (ADM-SVC, other trusted sibling services) must include:
 * <pre>
 *   X-Internal-Secret: &lt;INTERNAL_SERVICE_SECRET&gt;
 * </pre>
 *
 * <p>This also guards {@code /internal/auth/impersonation-token} — a shared
 * secret is sufficient since these endpoints are called over a private
 * network by trusted internal services, not external clients.
 *
 * <p>Spring Security must list {@code /internal/**} as {@code permitAll()} so
 * that JWT authentication is not required. This filter then performs its own
 * authentication check before the request reaches any controller.
 */
@Slf4j
@Component
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String HEADER_SECRET        = "X-Internal-Secret";

    @Value("${internal-service-secret}")
    private String expectedSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (!path.startsWith(INTERNAL_PATH_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String provided = request.getHeader(HEADER_SECRET);
        if (provided == null || !provided.equals(expectedSecret)) {
            log.warn("internal.auth.rejected path={} ip={}",
                    path, request.getRemoteAddr());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }

        log.debug("internal.auth.passed path={}", path);
        chain.doFilter(request, response);
    }
}
