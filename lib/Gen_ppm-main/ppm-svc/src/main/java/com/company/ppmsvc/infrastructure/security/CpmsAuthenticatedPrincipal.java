package com.company.ppmsvc.infrastructure.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Authenticated principal populated from a verified CPMS JWT.
 *
 * <p>Permission resolution is handled separately at request time by
 * {@link RolePermissionResolver} using {@code roleId} as the lookup key
 * against Redis. The JWT carries no permission claims.
 */
public record CpmsAuthenticatedPrincipal(
        UUID         userId,
        UUID         tenantId,
        String       tenantSlug,
        UUID         roleId,
        CpmsUserType userType,
        String       sessionId,
        String       jti,
        Instant      expiresAt,
        String       username,
        String       userEmail,
        String       tenantName
) {

    public CpmsAuthenticatedPrincipal(
            UUID userId, UUID tenantId, String tenantSlug, UUID roleId,
            CpmsUserType userType, String sessionId, String jti, Instant expiresAt) {
        this(userId, tenantId, tenantSlug, roleId, userType, sessionId, jti, expiresAt, null, null, null);
    }

    public boolean isSuperAdmin() {
        return userType != null && userType.isSuperAdmin();
    }

    public boolean isImpersonating() {
        return userType == CpmsUserType.SUPER_ADMIN_IMPERSONATING;
    }

    public boolean isProspect() {
        return userType == CpmsUserType.PROSPECT;
    }

    @Override
    public String toString() {
        return userId != null ? userId.toString() : "anonymous";
    }
}
