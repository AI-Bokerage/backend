package com.company.ppmsvc.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Platform-level authorization filter for PPM-SVC.
 *
 * <p>Runs inside the Spring Security filter chain immediately after
 * {@link JtiRevocationFilter}. Applies three rules in order:
 * <ol>
 *   <li><b>Public paths</b> — passed through without authentication. The five
 *       public endpoints (plan catalog GETs, pricing resolve, promo validation)
 *       must remain accessible before the caller has a session.</li>
 *   <li><b>SUPER_ADMIN</b> — bypasses the Redis permission lookup entirely.
 *       Super admin tokens carry no {@code role_id} so {@code SMEMBERS role:null}
 *       would always return empty, producing a false 403.</li>
 *   <li><b>Authenticated tenant users</b> — must hold {@code ppm.read} in their
 *       Redis role set. {@code ppm.read} is assigned to Employee, Admin, and all
 *       C-Suite roles via ADM-SVC's default permission catalog.</li>
 * </ol>
 *
 * <p>Write-path enforcement (SUPER_ADMIN only for mutations) is handled by the
 * subsequent {@link PpmAdminAuthorizationFilter}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PpmAccessAuthorizationFilter extends OncePerRequestFilter {

    private static final String PPM_READ = "ppm.read";
    private static final String FORBIDDEN_BODY =
        "{\"error\":\"Forbidden\",\"message\":\"ppm.read permission required\"}";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /**
     * GET paths that are fully public — no bearer token required.
     * Matched against the request's servlet path using Ant patterns.
     *
     * <p>Ant {@code /*} matches exactly ONE path segment; {@code /**} matches zero or more.
     * Use {@code /**} for sub-resource paths (e.g. {@code /plans/*}/versions/latest}).
     */
    private static final List<String> PUBLIC_GET_PATHS = List.of(
        "/api/v1/ppm/plans",                        // plan list (auto-filtered to PUBLIC visibility)
        "/api/v1/ppm/plans/*",                      // plan by id  (single segment — excludes sub-resources)
        "/api/v1/ppm/plans/slug/*",                 // plan by slug
        "/api/v1/ppm/plans/code/*",                 // plan by code — REG-SVC resolves ppmPlanId during plan selection
        "/api/v1/ppm/plans/*/versions/latest",      // BSM C2 version-locking during checkout
        "/api/v1/ppm/add-ons/*/prices/active",      // BSM C4 add-on price resolution
        "/api/v1/ppm/plans/*/entitlements/resolved",// USG-SVC limit seeding (service-to-service, no user JWT)
        "/api/v1/ppm/plans/*/modules",               // REG-SVC signup pricing page feature list
        "/api/v1/ppm/plan-versions/*/meta",         // BSM event publisher + drift detection
        "/api/v1/ppm/plan-versions/*/limits"        // BSM downgrade preflight
    );

    /**
     * POST paths that are fully public — callers have no session during checkout.
     */
    private static final List<String> PUBLIC_POST_PATHS = List.of(
        "/api/v1/ppm/prices/resolve",
        "/api/v1/ppm/promo-codes/validate"
    );

    private final RedisRolePermissionResolver rolePermissionResolver;

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

        String servletPath = request.getServletPath();
        String path = (servletPath != null && !servletPath.isBlank())
            ? servletPath : request.getRequestURI();
        String method = request.getMethod();

        // ── 1. Public paths — pass through without any auth check ────────────
        if (isPublicPath(method, path)) {
            chain.doFilter(request, response);
            return;
        }

        // ── 2. Require an authenticated principal for all non-public paths ────
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CpmsAuthenticatedPrincipal principal)) {
            log.debug("ppm.access.filter.no_principal path={}", path);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        // ── 3. SUPER_ADMIN bypass — no role_id, so Redis lookup is meaningless ─
        if (principal.isSuperAdmin()) {
            log.debug("ppm.access.granted.super_admin userId={}", principal.userId());
            chain.doFilter(request, response);
            return;
        }

        // ── 4. Tenant users must hold ppm.read ────────────────────────────────
        List<UUID> roleIds = principal.roleId() != null
            ? List.of(principal.roleId())
            : List.of();

        Set<String> permissions = rolePermissionResolver.resolveAll(roleIds);

        if (!permissions.contains(PPM_READ)) {
            log.warn("ppm.read.denied userId={} roleId={}", principal.userId(), principal.roleId());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(FORBIDDEN_BODY);
            return;
        }

        log.debug("ppm.access.granted userId={}", principal.userId());
        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String method, String path) {
        if (HttpMethod.GET.name().equals(method)) {
            return PUBLIC_GET_PATHS.stream().anyMatch(p -> MATCHER.match(p, path));
        }
        if (HttpMethod.POST.name().equals(method)) {
            return PUBLIC_POST_PATHS.stream().anyMatch(p -> MATCHER.match(p, path));
        }
        return false;
    }
}
