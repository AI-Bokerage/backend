package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.infrastructure.persistence.entity.ModuleEntity;
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
 * Spring Data JPA repository for {@link ModuleEntity}.
 *
 * <p>This interface is an infrastructure detail.  Application services must
 * not use it directly — they depend on
 * {@link com.company.ppmsvc.module.port.ModuleRepositoryPort}, whose
 * implementation delegates to this repository via the persistence adapter.
 */
public interface ModuleJpaRepository extends JpaRepository<ModuleEntity, UUID> {

    /** Returns the module with the given canonical code, or empty if not found. */
    Optional<ModuleEntity> findByCode(ModuleCode code);

    /** Returns {@code true} if a module with the given code already exists. */
    boolean existsByCode(ModuleCode code);

    /** Returns all non-deleted modules ordered by code ascending. */
    List<ModuleEntity> findAllByOrderByCodeAsc();

    /**
     * Soft-deletes a module by setting {@code deleted_at}, {@code updated_at},
     * and {@code updated_by} in a single atomic UPDATE.
     *
     * <p>Returns the number of rows affected (0 = not found or already deleted).
     */
    @Transactional
    @Modifying
    @Query("UPDATE ModuleEntity m SET m.deletedAt = :now, m.updatedAt = :now, m.updatedBy = :actorId " +
           "WHERE m.id = :id AND m.deletedAt IS NULL")
    int softDeleteById(@Param("id") UUID id,
                       @Param("now") Instant now,
                       @Param("actorId") UUID actorId);
}
