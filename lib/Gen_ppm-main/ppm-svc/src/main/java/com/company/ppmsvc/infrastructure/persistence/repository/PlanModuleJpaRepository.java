package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.PlanModuleEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link PlanModuleEntity}.
 *
 * <p>This interface is an infrastructure detail.  Application services must
 * not use it directly — they depend on
 * {@link com.company.ppmsvc.planmodule.port.PlanModuleRepositoryPort}, whose
 * implementation delegates to this repository via the persistence adapter.
 *
 * <p>Bulk delete methods use explicit JPQL {@code @Query} to issue a single
 * {@code DELETE} statement rather than the N+1 behaviour of derived delete.
 */
public interface PlanModuleJpaRepository extends JpaRepository<PlanModuleEntity, UUID> {

    /** Returns all mappings for the given plan, ordered by creation time. */
    List<PlanModuleEntity> findByPlanIdOrderByCreatedAtAsc(UUID planId);

    /** Returns all mappings that reference the given module. */
    List<PlanModuleEntity> findByModuleId(UUID moduleId);

    /** Returns {@code true} if a (planId, moduleId) mapping already exists. */
    boolean existsByPlanIdAndModuleId(UUID planId, UUID moduleId);

    /**
     * Deletes the mapping for (planId, moduleId) in a single {@code DELETE}.
     * Returns the number of rows deleted (0 = mapping did not exist).
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM PlanModuleEntity m WHERE m.planId = :planId AND m.moduleId = :moduleId")
    int deleteByPlanIdAndModuleId(@Param("planId") UUID planId,
                                  @Param("moduleId") UUID moduleId);

    /**
     * Deletes all mappings for a plan in a single {@code DELETE}.
     * Used by the atomic replace operation.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM PlanModuleEntity m WHERE m.planId = :planId")
    void deleteAllByPlanId(@Param("planId") UUID planId);
}
