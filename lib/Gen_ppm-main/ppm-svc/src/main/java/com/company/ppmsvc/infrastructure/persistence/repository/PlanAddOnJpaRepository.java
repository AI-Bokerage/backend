package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.PlanAddOnEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link PlanAddOnEntity}.
 *
 * <p>This interface is an infrastructure detail.  Application services must
 * not use it directly — they depend on
 * {@link com.company.ppmsvc.planaddon.port.PlanAddOnRepositoryPort}, whose
 * implementation delegates to this repository via the persistence adapter.
 *
 * <p>Bulk delete methods use explicit JPQL {@code @Query} to issue a single
 * {@code DELETE} statement rather than the N+1 behaviour of derived delete.
 */
public interface PlanAddOnJpaRepository extends JpaRepository<PlanAddOnEntity, UUID> {

    /** Returns all assignments for the given plan, ordered by creation time. */
    List<PlanAddOnEntity> findByPlanIdOrderByCreatedAtAsc(UUID planId);

    /** Returns all assignments that reference the given add-on. */
    List<PlanAddOnEntity> findByAddOnId(UUID addOnId);

    /** Returns {@code true} if a (planId, addOnId) assignment already exists. */
    boolean existsByPlanIdAndAddOnId(UUID planId, UUID addOnId);

    /**
     * Deletes the assignment for (planId, addOnId) in a single {@code DELETE}.
     * Returns the number of rows deleted (0 = assignment did not exist).
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM PlanAddOnEntity p WHERE p.planId = :planId AND p.addOnId = :addOnId")
    int deleteByPlanIdAndAddOnId(@Param("planId") UUID planId,
                                 @Param("addOnId") UUID addOnId);

    /**
     * Deletes all assignments for a plan in a single {@code DELETE}.
     * Used by the atomic replace operation.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM PlanAddOnEntity p WHERE p.planId = :planId")
    void deleteAllByPlanId(@Param("planId") UUID planId);
}
