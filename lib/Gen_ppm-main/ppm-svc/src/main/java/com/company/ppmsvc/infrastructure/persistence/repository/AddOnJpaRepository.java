package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.AddOnEntity;
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
 * Spring Data JPA repository for {@link AddOnEntity}.
 *
 * <p>This interface is an infrastructure detail.  Application services must
 * not use it directly — they depend on
 * {@link com.company.ppmsvc.addon.port.AddOnRepositoryPort}, whose
 * implementation delegates to this repository via the persistence adapter.
 */
public interface AddOnJpaRepository extends JpaRepository<AddOnEntity, UUID> {

    /** Returns the add-on with the given code, or empty if not found. */
    Optional<AddOnEntity> findByCode(String code);

    /** Returns {@code true} if an active add-on with the given code already exists. */
    boolean existsByCode(String code);

    /** Returns all non-deleted add-ons ordered by code ascending. */
    List<AddOnEntity> findAllByOrderByCodeAsc();

    /**
     * Soft-deletes an add-on by setting {@code deleted_at}, {@code updated_at},
     * and {@code updated_by} in a single atomic UPDATE.
     *
     * <p>Returns the number of rows affected (0 = not found or already deleted).
     */
    @Transactional
    @Modifying
    @Query("UPDATE AddOnEntity a SET a.deletedAt = :now, a.updatedAt = :now, a.updatedBy = :actorId " +
           "WHERE a.id = :id AND a.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id,
                       @Param("now") Instant now,
                       @Param("actorId") UUID actorId);
}
