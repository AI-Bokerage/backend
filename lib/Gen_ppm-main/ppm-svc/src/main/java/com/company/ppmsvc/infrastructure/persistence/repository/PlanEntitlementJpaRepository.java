package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.PlanEntitlementEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link PlanEntitlementEntity}.
 *
 * <p>This interface is an infrastructure detail.  Application services must
 * not use it directly — they depend on
 * {@link com.company.ppmsvc.planentitlement.port.PlanEntitlementRepositoryPort}, whose
 * implementation delegates to this repository via the persistence adapter.
 *
 * <p>Bulk delete methods use explicit JPQL {@code @Query} to issue a single
 * {@code DELETE} statement rather than the N+1 behaviour of derived delete.
 */
public interface PlanEntitlementJpaRepository extends JpaRepository<PlanEntitlementEntity, UUID> {

    /** Returns all assignments for the given plan, ordered by creation time. */
    List<PlanEntitlementEntity> findByPlanIdOrderByCreatedAtAsc(UUID planId);

    /** Returns all assignments that reference the given entitlement definition. */
    List<PlanEntitlementEntity> findByEntitlementId(UUID entitlementId);

    /** Returns {@code true} if a (planId, entitlementId) assignment already exists. */
    boolean existsByPlanIdAndEntitlementId(UUID planId, UUID entitlementId);

    /**
     * Deletes the assignment for (planId, entitlementId) in a single {@code DELETE}.
     * Returns the number of rows deleted (0 = assignment did not exist).
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM PlanEntitlementEntity e WHERE e.planId = :planId AND e.entitlementId = :entitlementId")
    int deleteByPlanIdAndEntitlementId(@Param("planId") UUID planId,
                                       @Param("entitlementId") UUID entitlementId);

    /**
     * Deletes all assignments for a plan in a single {@code DELETE}.
     * Used by the atomic replace operation.
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM PlanEntitlementEntity e WHERE e.planId = :planId")
    void deleteAllByPlanId(@Param("planId") UUID planId);
}
