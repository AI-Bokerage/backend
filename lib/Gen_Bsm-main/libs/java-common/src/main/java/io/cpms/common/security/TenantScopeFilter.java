package io.cpms.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Enforces tenant isolation for every authenticated request.
 *
 * <h3>Tenant isolation rules</h3>
 * <ul>
 *   <li><strong>TENANT_USER, CLIENT, PROSPECT</strong> — if the URL contains a
 *       {@code /tenants/{uuid}} segment the UUID must equal {@code jwt.tenant_id},
 *       otherwise the request is rejected with 403.</li>
 *   <li><strong>SUPER_ADMIN</strong> — bypasses the tenant scope check entirely.
 *       A SUPER_ADMIN's JWT carries {@code tenant_id = 00000000-…} (platform sentinel),
 *       so the check would always fail if enforced.</li>
 *   <li><strong>SUPER_ADMIN_IMPERSONATING</strong> — does <em>not</em> bypass tenant
 *       isolation. The impersonation JWT carries the real impersonated tenant's ID so the
 *       same cross-tenant check applies. An impersonating admin can only access resources
 *       belonging to the tenant they impersonated. All accesses are audit-logged.</li>
 * </ul>
 *
 * <p>Also populates MDC keys {@code tenantId}, {@code tenantSlug}, {@code userId},
 * {@code sessionId} for structured logging. Sets {@code impersonation=true} when
 * the principal is impersonating.
 */
public class TenantScopeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantScopeFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof CpmsAuthenticatedPrincipal principal) {

            // ── MDC population ────────────────────────────────────────────────────
            MDC.put("userId",     principal.userId() != null ? principal.userId().toString() : "");
            MDC.put("tenantId",   principal.tenantId() != null ? principal.tenantId().toString() : "");
            MDC.put("tenantSlug", principal.tenantSlug() != null ? principal.tenantSlug() : "");
            MDC.put("sessionId",  principal.sessionId() != null ? principal.sessionId() : "");

            if (principal.isImpersonating()) {
                MDC.put("impersonation", "true");
                // Audit every request made under impersonation
                log.info("security.impersonation.request method={} uri={} superAdminId={} impersonatedTenant={}",
                        request.getMethod(), request.getRequestURI(),
                        principal.userId(), principal.tenantId());
            }

            // ── Tenant scope enforcement ──────────────────────────────────────────
            // Only pure SUPER_ADMIN (tenantId = platform sentinel) bypasses this check.
            // SUPER_ADMIN_IMPERSONATING carries the real tenant's ID and is bound to it.
            boolean bypassTenantScope = (principal.userType() == CpmsUserType.SUPER_ADMIN);

            if (!bypassTenantScope) {
                String pathTenantId = extractTenantIdFromPath(request.getRequestURI());
                if (pathTenantId != null) {
                    try {
                        UUID pathTenant = UUID.fromString(pathTenantId);
                        if (!pathTenant.equals(principal.tenantId())) {
                            log.warn("security.cross_tenant_blocked userId={} userType={} " +
                                     "jwtTenant={} pathTenant={} uri={}",
                                    principal.userId(), principal.userType(),
                                    principal.tenantId(), pathTenant, request.getRequestURI());
                            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                    "Cross-tenant access denied");
                            return;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // segment is not a UUID — skip
                    }
                }
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("tenantId");
            MDC.remove("userId");
            MDC.remove("tenantSlug");
            MDC.remove("sessionId");
            MDC.remove("impersonation");
        }
    }

    private static String extractTenantIdFromPath(String uri) {
        if (uri == null) return null;
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("tenants".equals(parts[i]) && parts[i + 1] != null && !parts[i + 1].isBlank()) {
                return parts[i + 1].split("\\?")[0];
            }
        }
        return null;
    }
}
