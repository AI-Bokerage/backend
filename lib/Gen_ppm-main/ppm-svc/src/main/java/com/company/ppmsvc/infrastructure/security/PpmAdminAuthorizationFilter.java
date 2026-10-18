package com.company.ppmsvc.infrastructure.security;

import com.company.ppmsvc.exception.AccessDeniedException;
import com.company.ppmsvc.security.PpmAuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects PPM-SVC's catalog-wide mutation endpoints.
 *
 * <p>Runs after {@link PpmAccessAuthorizationFilter} in the Security chain.
 * By the time this filter executes, all authenticated non-public requests have
 * already been verified to carry {@code ppm.read}. This filter adds a second
 * gate: any write method (POST, PUT, PATCH, DELETE) on a non-public path
 * delegates to {@link PpmAuthorizationService#authorizeAdminWrite()}.
 *
 * <p>Authorization itself lives entirely in {@link PpmAuthorizationService} —
 * this filter only requires a principal to be present (401) and translates
 * an {@link AccessDeniedException} from the delegate into a 403.
 *
 * <p>Public POST paths ({@code /prices/resolve} and {@code /promo-codes/validate})
 * and the promotion-pricing quote endpoint (which authorizes itself via
 * {@code authorizeQuote()}) are excluded from this gate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PpmAdminAuthorizationFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** Mutation HTTP methods that trigger the admin-write gate. */
    private static final List<String> WRITE_METHODS =
        List.of("POST", "PUT", "PATCH", "DELETE");

    /**
     * Write paths that are exempt from the admin-write gate — either fully
     * public, or authorized through their own dedicated SPI hook.
     */
    private static final List<String> EXEMPT_WRITE_PATHS = List.of(
        "/api/v1/ppm/prices/resolve",
        "/api/v1/ppm/promo-codes/validate",
        "/api/v1/ppm/quotes"
    );

    private static final String FORBIDDEN_BODY =
        "{\"error\":\"Forbidden\",\"message\":\"This endpoint requires Super Admin access\"}";

    private final PpmAuthorizationService authorizationService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String path = (servletPath != null && !servletPath.isBlank())
            ? servletPath : request.getRequestURI();
        String method = request.getMethod();
        if (path.startsWith("/actuator")
                || path.startsWith("/v1/docs")
                || path.startsWith("/v1/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")) {
            return true;
        }
        // GET requests are handled by PpmAccessAuthorizationFilter; no write check needed.
        return !WRITE_METHODS.contains(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain) throws ServletException, IOException {

        String servletPath = request.getServletPath();
        String path = (servletPath != null && !servletPath.isBlank())
            ? servletPath : request.getRequestURI();
        String method = request.getMethod();

        // Exempt paths: fully public, or authorized through their own SPI hook.
        if (isExemptWritePath(path)) {
            chain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CpmsAuthenticatedPrincipal principal)) {
            log.debug("ppm.admin.filter.no_principal method={} path={}", method, path);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        try {
            authorizationService.authorizeAdminWrite();
        } catch (AccessDeniedException ex) {
            log.warn("ppm.admin.access.denied userId={} method={} path={}",
                principal.userId(), method, path);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(FORBIDDEN_BODY);
            return;
        }

        log.debug("ppm.admin.access.granted userId={} method={} path={}",
            principal.userId(), method, path);
        chain.doFilter(request, response);
    }

    private boolean isExemptWritePath(String path) {
        return EXEMPT_WRITE_PATHS.stream()
            .anyMatch(p -> MATCHER.match(p, path));
    }
}
