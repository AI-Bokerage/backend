package com.company.ppmsvc.application.util;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable value object representing the currently authenticated principal.
 *
 * <p>PPM-SVC is a catalog service with no per-tenant isolation, so only
 * {@code userId} and {@code roles} are tracked — there is no {@code tenantId}
 * field. The {@code userId} is used for audit columns (created_by / updated_by).
 */
public record AuthenticatedUser(
    UUID        userId,
    Set<String> roles
) {

    public AuthenticatedUser {
        roles = roles == null ? Collections.emptySet() : Collections.unmodifiableSet(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
