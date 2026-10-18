package io.cpms.common.security;

import io.platform.security.AuthenticatedPrincipal;
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
) implements AuthenticatedPrincipal {

    /**
     * Backward-compatible constructor for the ~60 existing call sites (mostly tests) predating
     * the {@code username}/{@code userEmail}/{@code tenantName} claims — those three default to
     * {@code null} rather than forcing every caller to be touched at once.
     */
    public CpmsAuthenticatedPrincipal(
            UUID userId, UUID tenantId, String tenantSlug, UUID roleId,
            CpmsUserType userType, String sessionId, String jti, Instant expiresAt) {
        this(userId, tenantId, tenantSlug, roleId, userType, sessionId, jti, expiresAt, null, null, null);
    }

    public boolean isSuperAdmin() {
        return userType != null && userType.isSuperAdmin();
    }

    /**
     * SPI capability method. Deliberately narrower than {@link #isSuperAdmin()} — an
     * unconditional cross-tenant bypass applies only to a bare {@code SUPER_ADMIN}, never to
     * {@code SUPER_ADMIN_IMPERSONATING}, which stays bound to the impersonated tenant.
     */
    @Override
    public boolean bypassesTenantScoping() {
        return userType == CpmsUserType.SUPER_ADMIN;
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
