package com.example.authsvc.application.impl;

import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared session and token revocation logic used by:
 * <ul>
 *   <li>{@code POST /internal/auth/users/{id}/revoke-sessions} — called by ADM-SVC offboarding</li>
 *   <li>{@code UserDeactivatedConsumer} — called when user.deactivated event arrives</li>
 * </ul>
 *
 * <p>Revocation is a three-step operation that must be consistent:
 * <ol>
 *   <li>Soft-delete all active rows in {@code auth_sessions} (DB)</li>
 *   <li>Mark all refresh token audit records as revoked in {@code auth_refresh_tokens} (DB)</li>
 *   <li>Delete all active refresh tokens from Redis via the user-sessions index</li>
 * </ol>
 *
 * <p>Steps 1–2 are transactional (committed together). Step 3 is Redis and therefore
 * cannot participate in the DB transaction — it runs after commit. In the event of a
 * crash between steps 2 and 3, Redis tokens remain valid until their TTL expires
 * (maximum 7 days). This is the known at-least-once revocation window.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalSessionRevocationService {

    private final AuthSessionJpaRepository      sessionRepo;
    private final AuthRefreshTokenJpaRepository refreshTokenAuditRepo;
    private final AuthUserJpaRepository         userRepo;
    private final RefreshTokenStore             refreshTokenStore;

    /**
     * Revokes all sessions and tokens for a user.
     *
     * <p>Idempotent — calling multiple times with the same userId has no additional
     * effect: sessions already deactivated are not touched, Redis DELs on absent
     * keys are no-ops.
     *
     * @param userId   the user whose sessions to revoke
     * @param tenantId used for audit logging only
     */
    @Transactional
    public void revokeAllForUser(UUID userId, UUID tenantId) {
        Instant now = Instant.now();

        // 1. Soft-delete active DB sessions
        int revokedSessions = sessionRepo.deactivateAllByUserId(userId, now);

        // 2. Mark DB refresh token audit records as revoked
        int revokedTokens = refreshTokenAuditRepo.revokeAllByUserId(userId, now);

        log.info("auth.session.revocation.db userId={} tenantId={} sessions={} tokens={}",
                userId, tenantId, revokedSessions, revokedTokens);

        // 3. Clear Redis active token store (outside TX — at-least-once semantics)
        refreshTokenStore.revokeAllByUserId(userId);

        log.info("auth.session.revocation.redis userId={} tenantId={}", userId, tenantId);
    }

    /**
     * Deactivates the user account and revokes all sessions.
     * Called by {@code UserDeactivatedConsumer}.
     */
    @Transactional
    public void deactivateAndRevoke(UUID userId, UUID tenantId) {
        userRepo.findById(userId).ifPresent(u -> {
            if (u.isActive()) {
                u.setActive(false);
                userRepo.save(u);
                log.info("auth.user.deactivated userId={} tenantId={}", userId, tenantId);
            }
        });
        revokeAllForUser(userId, tenantId);
    }
}
