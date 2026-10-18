package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.enums.BsmRole;
import java.util.UUID;

/**
 * Authorization abstraction for BSM-SVC.
 * Implementations will wire in the actual auth mechanism (JWT claims, header-based, etc.).
 * No Spring Security dependency — the domain layer stays framework-agnostic.
 */
public interface BsmAuthorizationPort {

    /**
     * Returns true if the given principal has at least the required role for the given tenant.
     *
     * @param principalId the actor performing the action
     * @param tenantId    the tenant context
     * @param required    the minimum role required
     */
    boolean hasRole(UUID principalId, UUID tenantId, BsmRole required);
}
