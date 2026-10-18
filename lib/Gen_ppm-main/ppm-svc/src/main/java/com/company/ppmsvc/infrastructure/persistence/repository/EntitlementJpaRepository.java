package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.EntitlementEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link EntitlementEntity}.
 *
 * <p>This interface is an infrastructure detail.  Application services must
 * not use it directly — they depend on
 * {@link com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort}, whose
 * implementation delegates to this repository via the persistence adapter.
 */
public interface EntitlementJpaRepository extends JpaRepository<EntitlementEntity, UUID> {

    /** Returns the entitlement with the given code, or empty if not found (or soft-deleted). */
    Optional<EntitlementEntity> findByCode(String code);

    /** Returns {@code true} if an active entitlement with the given code already exists. */
    boolean existsByCode(String code);

    /** Returns all non-deleted entitlements ordered by code ascending. */
    List<EntitlementEntity> findAllByOrderByCodeAsc();

    /**
     * Soft-deletes an entitlement by setting {@code deleted_at}, {@code updated_at},
     * and {@code updated_by} in a single atomic UPDATE.
     *
     * <p>Returns the number of rows affected (0 = not found or already deleted).
     */
    @Transactional
    @Modifying
    @Query("UPDATE EntitlementEntity e SET e.deletedAt = :now, e.updatedAt = :now, e.updatedBy = :actorId " +
           "WHERE e.id = :id AND e.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id,
                       @Param("now") Instant now,
                       @Param("actorId") UUID actorId);
}
