package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.AuthRefreshTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuthRefreshTokenEntity}.
 *
 * <p>All lookups are performed against the {@code token_hash} column —
 * the plaintext token must be hashed via
 * {@link com.example.authsvc.common.util.RefreshTokenHashUtil} before
 * calling any method on this repository.
 */
@Repository
public interface AuthRefreshTokenJpaRepository extends JpaRepository<AuthRefreshTokenEntity, UUID> {

    /**
     * Looks up a non-revoked, non-expired token by its SHA-256 hash.
     * Uses a pessimistic write lock to prevent concurrent rotation of the same token.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
           SELECT t FROM AuthRefreshTokenEntity t
            WHERE t.tokenHash = :tokenHash
              AND t.used      = false
              AND t.revokedAt IS NULL
              AND t.expiresAt > :now
           """)
    Optional<AuthRefreshTokenEntity> findValidByTokenHash(@Param("tokenHash") String tokenHash,
                                                          @Param("now")       Instant now);

    /**
     * Looks up any token by hash regardless of used/revoked state.
     * Used exclusively for replay attack detection.
     */
    @Query("SELECT t FROM AuthRefreshTokenEntity t WHERE t.tokenHash = :tokenHash")
    Optional<AuthRefreshTokenEntity> findByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Bulk-revokes all refresh tokens belonging to a user.
     * Used during logout-all and account suspension.
     */
    @Modifying
    @Query("""
           UPDATE AuthRefreshTokenEntity t
              SET t.revokedAt = :revokedAt
            WHERE t.userId    = :userId
              AND t.revokedAt IS NULL
           """)
    int revokeAllByUserId(@Param("userId")    UUID userId,
                          @Param("revokedAt") Instant revokedAt);

    /**
     * Bulk-revokes all refresh tokens belonging to a user except those attached to
     * the current session.
     */
    @Modifying
    @Query("""
           UPDATE AuthRefreshTokenEntity t
              SET t.revokedAt = :revokedAt
            WHERE t.userId    = :userId
              AND t.sessionId <> :currentSessionId
              AND t.revokedAt IS NULL
           """)
    int revokeAllByUserIdExceptSession(@Param("userId") UUID userId,
                                       @Param("currentSessionId") UUID currentSessionId,
                                       @Param("revokedAt") Instant revokedAt);

    /**
     * Finds active refresh-token families for all other sessions of a user so the
     * active-token store can revoke them without touching the current device.
     */
    @Query("""
           SELECT DISTINCT t.familyId
             FROM AuthRefreshTokenEntity t
            WHERE t.userId    = :userId
              AND t.sessionId <> :currentSessionId
              AND t.revokedAt IS NULL
           """)
    List<UUID> findActiveFamilyIdsByUserIdExcludingSession(@Param("userId") UUID userId,
                                                           @Param("currentSessionId") UUID currentSessionId);

    /**
     * Revokes all refresh tokens linked to a specific session.
     */
    @Modifying
    @Query("""
           UPDATE AuthRefreshTokenEntity t
              SET t.revokedAt = :revokedAt
            WHERE t.sessionId = :sessionId
              AND t.revokedAt IS NULL
           """)
    int revokeAllBySessionId(@Param("sessionId") UUID sessionId,
                             @Param("revokedAt")  Instant revokedAt);

    /** Deletes tokens that have been expired for longer than the given cutoff (data retention). */
    @Modifying
    @Query("DELETE FROM AuthRefreshTokenEntity t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
