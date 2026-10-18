package com.company.bsmsvc.domain.port;

import java.util.UUID;

/**
 * Tenant isolation guard for use-case methods that receive a claimed tenantId
 * (request body, query parameter, or DTO field) rather than relying on a
 * filter-level convention. No framework/security dependency — the host wires
 * this to its actual authentication mechanism (JWT claims, header-based, etc.).
 */
public interface TenantScopePort {

    /**
     * Asserts that the current caller is permitted to act on {@code requestedTenantId}.
     * Implementations may throw an unchecked exception when access is denied.
     */
    void assertTenantAccess(UUID requestedTenantId);

    /**
     * Resolves the effective tenantId to use in list/search filter operations,
     * scoping non-privileged callers to their own tenant even when the filter is null.
     */
    UUID resolveEffectiveTenantId(UUID requestedTenantId);
}
