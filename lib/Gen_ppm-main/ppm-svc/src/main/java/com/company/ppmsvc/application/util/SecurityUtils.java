package com.company.ppmsvc.application.util;

import com.company.ppmsvc.exception.AccessDeniedException;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Security utility that extracts the authenticated principal from
 * {@link SecurityContextHolder}.
 *
 * <p>PPM-SVC is a catalog service — there is no per-tenant isolation, so
 * tenant context is not enforced here. The {@code userId} is available for
 * audit purposes (created_by / updated_by fields).
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<AuthenticatedUser> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        if (!(auth.getPrincipal() instanceof CpmsAuthenticatedPrincipal principal)) return Optional.empty();
        return Optional.of(new AuthenticatedUser(principal.userId(), Set.of()));
    }

    public static UUID requireCurrentUserId() {
        return requireCurrentUser().userId();
    }

    public static AuthenticatedUser requireCurrentUser() {
        return getCurrentUser().orElseThrow(AccessDeniedException::new);
    }
}
