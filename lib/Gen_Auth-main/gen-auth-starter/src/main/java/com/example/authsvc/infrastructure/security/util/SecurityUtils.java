package com.example.authsvc.infrastructure.security.util;

import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SecurityUtils {

    public static Optional<UUID> getCurrentUserId() {
        return getPrincipal().map(AuthenticatedUser::getUserId);
    }

    public static Optional<UUID> getCurrentTenantId() {
        return getPrincipal().map(AuthenticatedUser::getTenantId);
    }

    /**
     * Backward-compat scalar accessor — returns the first assigned role UUID.
     * Prefer {@link #getCurrentRoleIds()} for multi-role permission checks.
     */
    public static Optional<UUID> getCurrentRoleId() {
        return getPrincipal().map(AuthenticatedUser::getRoleId);
    }

    /**
     * Returns the full list of ADM role UUIDs for the current authenticated user.
     * Empty list if unauthenticated or if the JWT predates Phase 4.
     */
    public static List<UUID> getCurrentRoleIds() {
        return getPrincipal().map(AuthenticatedUser::getRoleIds).orElse(List.of());
    }

    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private static Optional<AuthenticatedUser> getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
