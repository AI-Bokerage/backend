package com.example.authsvc.infrastructure.persistence.repository;

import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuthSessionEntity}.
 *
 * <p>Provides query methods for the common session management operations.
 * Complex business operations (e.g. session invalidation with event publishing)
 * should live in an application service, not here.
 */
@Repository
public interface AuthSessionJpaRepository extends JpaRepository<AuthSessionEntity, UUID> {

    /** Finds all active sessions belonging to a user. */
    List<AuthSessionEntity> findAllByUserIdAndActiveTrue(UUID userId);

    /** Finds the single active session for a user in a given tenant context. */
    Optional<AuthSessionEntity> findByIdAndActiveTrue(UUID sessionId);

    /** Counts the number of active sessions for a user (rate limiting / device limits). */
    long countByUserIdAndActiveTrue(UUID userId);

    /**
     * Bulk-deactivates all active sessions for a user.
     * Used during logout-all-devices and account suspension flows.
     */
    @Modifying
    @Query("""
           UPDATE AuthSessionEntity s
              SET s.active    = false,
                  s.revokedAt = :revokedAt
            WHERE s.userId    = :userId
              AND s.active    = true
           """)
    int deactivateAllByUserId(@Param("userId") UUID userId,
                              @Param("revokedAt") Instant revokedAt);

    /**
     * Bulk-deactivates all active sessions for a user except the current session.
     * Used when a security-sensitive action should keep only the in-hand device alive.
     */
    @Modifying
    @Query("""
           UPDATE AuthSessionEntity s
              SET s.active    = false,
                  s.revokedAt = :revokedAt
            WHERE s.userId    = :userId
              AND s.id       <> :currentSessionId
              AND s.active    = true
           """)
    int deactivateAllByUserIdExceptSession(@Param("userId") UUID userId,
                                           @Param("currentSessionId") UUID currentSessionId,
                                           @Param("revokedAt") Instant revokedAt);

    /**
     * Deactivates expired sessions (scheduled clean-up job).
     */
    @Modifying
    @Query("""
           UPDATE AuthSessionEntity s
              SET s.active = false
            WHERE s.active    = true
              AND s.expiresAt < :now
           """)
    int deactivateExpiredSessions(@Param("now") Instant now);
}
