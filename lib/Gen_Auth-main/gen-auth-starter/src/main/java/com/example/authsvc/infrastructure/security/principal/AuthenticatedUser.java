package com.example.authsvc.infrastructure.security.principal;

import com.example.authsvc.domain.enums.UserType;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable security principal populated from a verified JWT.
 *
 * <p>{@link #getRoleIds()} contains every ADM role UUID assigned to this user,
 * derived from the {@code role_ids[]} claim (Phase 4+). Permission resolution
 * unions all roles via {@code PermissionCacheStore.resolveAll(roleIds)}.
 *
 * <p>{@link #getRoleId()} is a backward-compatibility accessor that returns the
 * first element of {@code roleIds}, or {@code null} when the list is empty. Callers
 * that only need one role may continue using it; new callers should prefer
 * {@link #getRoleIds()}.
 */
@Getter
public class AuthenticatedUser {

    private final UUID       userId;
    private final UUID       tenantId;
    private final String     tenantSlug;
    private final List<UUID> roleIds;
    private final UserType   userType;
    private final String     sessionId;
    private final Instant    expiresAt;
    private final String     jti;

    public AuthenticatedUser(UUID userId, UUID tenantId, String tenantSlug,
                             List<UUID> roleIds, UserType userType,
                             String sessionId, Instant expiresAt, String jti) {
        this.userId     = userId;
        this.tenantId   = tenantId;
        this.tenantSlug = tenantSlug;
        this.roleIds    = (roleIds != null) ? List.copyOf(roleIds) : List.of();
        this.userType   = userType;
        this.sessionId  = sessionId;
        this.expiresAt  = expiresAt;
        this.jti        = jti;
    }

    /**
     * Backward-compatibility accessor. Returns the first assigned role UUID, or
     * {@code null} when no roles are present. Prefer {@link #getRoleIds()} for
     * multi-role permission resolution.
     */
    public UUID getRoleId() {
        return roleIds.isEmpty() ? null : roleIds.get(0);
    }

    @Override
    public String toString() { return userId.toString(); }
}
