package com.company.bsmsvc.infrastructure.security;

import com.company.bsmsvc.domain.port.TenantScopePort;
import io.cpms.common.security.CpmsAuthenticatedPrincipal;
import io.platform.security.AuthenticatedPrincipal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Service-layer tenant isolation guard for BSM-SVC.
 *
 * <p>BSM API paths do not follow the {@code /tenants/{uuid}/...} convention that
 * {@code TenantScopeFilter} from java-common enforces at the filter level. BSM
 * accepts {@code tenantId} in request bodies, query parameters, and DTO fields.
 * This component validates caller identity against those claimed tenant IDs at the
 * start of each service method.</p>
 *
 * <p>Two behaviours are provided:
 * <ul>
 *   <li>{@link #assertTenantAccess(UUID)} — for mutation methods where the caller
 *       explicitly supplies a tenantId (create, update, delete). Throws
 *       {@link AccessDeniedException} when the JWT tenant does not match.</li>
 *   <li>{@link #resolveEffectiveTenantId(UUID)} — for list/search methods where
 *       tenantId is an optional filter. Non-super-admin callers are always scoped
 *       to their JWT tenant, preventing cross-tenant enumeration via a null filter.</li>
 * </ul>
 */
@Component
public class BsmTenantScopeEnforcer implements TenantScopePort {

    private static final Logger log = LoggerFactory.getLogger(BsmTenantScopeEnforcer.class);

    /**
     * Extracts the authenticated {@link AuthenticatedPrincipal} from the current security
     * context, or {@code null} if no principal is present.
     *
     * <p>A {@code null} return indicates a system/scheduler context (no inbound HTTP request).
     * Callers that need to distinguish between "no principal" and "unexpected principal type"
     * should call this method rather than {@link #assertTenantAccess(UUID)}.</p>
     */
    public AuthenticatedPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedPrincipal principal) {
            return principal;
        }
        return null;
    }

    /**
     * Asserts that the authenticated caller is permitted to act on {@code requestedTenantId}.
     *
     * <ul>
     *   <li><strong>No principal</strong> (scheduler / system thread) — allowed unconditionally.
     *       Background jobs such as invoice renewal, dunning, and reconciliation run without a
     *       JWT; they are trusted internal callers and must not be blocked.</li>
     *   <li>{@code SUPER_ADMIN} — allowed unconditionally.</li>
     *   <li>{@code SUPER_ADMIN_IMPERSONATING} — bound to the impersonated tenant in the JWT.</li>
     *   <li>{@code TENANT_USER}, {@code CLIENT}, {@code PROSPECT} — JWT tenant ID must match
     *       {@code requestedTenantId} exactly.</li>
     * </ul>
     *
     * @throws AccessDeniedException if {@code requestedTenantId} is null, or if an authenticated
     *                               user's tenant does not match.
     */
    public void assertTenantAccess(UUID requestedTenantId) {
        if (requestedTenantId == null) {
            throw new AccessDeniedException("tenantId must not be null for tenant-scoped operations");
        }
        AuthenticatedPrincipal principal = getPrincipal();
        if (principal == null) {
            // System/scheduler context — no external caller, allow through
            return;
        }
        if (principal.bypassesTenantScoping()) {
            return;
        }
        if (!requestedTenantId.equals(principal.tenantId())) {
            String userType = principal instanceof CpmsAuthenticatedPrincipal cpms
                ? String.valueOf(cpms.userType()) : principal.getClass().getSimpleName();
            log.warn("bsm.tenant_scope.denied userId={} userType={} jwtTenant={} requestedTenant={}",
                principal.userId(), userType, principal.tenantId(), requestedTenantId);
            throw new AccessDeniedException(
                "Access denied: tenant " + requestedTenantId
                + " does not match authenticated tenant " + principal.tenantId()
            );
        }
    }

    /**
     * Resolves the effective tenantId to use in list/search filter operations.
     *
     * <p>No principal (system/scheduler context) and {@code SUPER_ADMIN} both pass the
     * requested tenantId through unchanged. All other authenticated users are always scoped
     * to their JWT tenant, preventing cross-tenant enumeration via a null filter.</p>
     *
     * @param requestedTenantId the tenantId provided by the caller (may be null)
     * @return the tenantId that must be used in the downstream repository query
     */
    public UUID resolveEffectiveTenantId(UUID requestedTenantId) {
        AuthenticatedPrincipal principal = getPrincipal();
        if (principal == null || principal.bypassesTenantScoping()) {
            return requestedTenantId;
        }
        return principal.tenantId();
    }
}
