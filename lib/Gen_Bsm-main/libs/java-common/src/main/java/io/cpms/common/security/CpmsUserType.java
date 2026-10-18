package io.cpms.common.security;

/**
 * Platform-wide user type enum — mirrors {@code auth-svc}'s {@code UserType}.
 * Embedded in every JWT {@code user_type} claim and used by all downstream services
 * to gate access at the route level.
 */
public enum CpmsUserType {
    TENANT_USER,
    CLIENT,
    SUPER_ADMIN,
    PROSPECT,
    SUPER_ADMIN_IMPERSONATING;

    public boolean isSuperAdmin() {
        return this == SUPER_ADMIN || this == SUPER_ADMIN_IMPERSONATING;
    }

    public boolean isImpersonating() {
        return this == SUPER_ADMIN_IMPERSONATING;
    }
}
