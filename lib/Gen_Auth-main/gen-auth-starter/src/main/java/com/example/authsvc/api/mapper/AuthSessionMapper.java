package com.example.authsvc.api.mapper;

import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public final class AuthSessionMapper {

    private AuthSessionMapper() {}

    /**
     * Creates an {@link AuthSessionEntity} for a standard user login.
     *
     * @param user    the authenticated user entity
     * @param roleIds the user's current role list (from {@code auth_user_roles})
     * @param sessionId the newly generated session UUID
     *
     * <p><strong>Transitional note:</strong> {@code auth_sessions.role_id} stores the
     * first element of {@code roleIds} as a backward-compatible snapshot. This column
     * is deprecated; no service should rely on it for authorization decisions.
     * Permission resolution uses {@code auth_user_roles} via the JWT {@code role_ids[]}
     * claim and Redis at request time.
     */
    public static AuthSessionEntity toEntity(AuthUserEntity user, List<UUID> roleIds,
                                             UUID sessionId, String ipAddress,
                                             String userAgent, String deviceFingerprint,
                                             Instant now) {
        return AuthSessionEntity.builder()
                .id(sessionId)
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .roleId(roleIds.isEmpty() ? null : roleIds.get(0))  // transitional
                .userType(user.getUserType())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceFingerprint(deviceFingerprint)
                .active(true)
                .impersonation(false)
                .lastActivityAt(now)
                .expiresAt(now.plus(7, ChronoUnit.DAYS))
                .build();
    }
}
