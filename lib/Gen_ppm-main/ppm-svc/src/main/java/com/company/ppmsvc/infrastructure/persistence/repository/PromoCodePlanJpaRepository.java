package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.PromoCodePlanEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link PromoCodePlanEntity}.
 *
 * <p>This interface is an infrastructure detail.  Application services must
 * not use it directly — they depend on
 * {@link com.company.ppmsvc.promocodeplan.port.PromoCodePlanRepositoryPort}, whose
 * implementation delegates to this repository via the persistence adapter.
 */
public interface PromoCodePlanJpaRepository extends JpaRepository<PromoCodePlanEntity, UUID> {

    /** Returns all restriction rows for the given promo code, in insertion order. */
    List<PromoCodePlanEntity> findByPromoCodeIdOrderByCreatedAtAsc(UUID promoCodeId);

    /** Returns all restriction rows that reference the given plan. */
    List<PromoCodePlanEntity> findByPlanId(UUID planId);

    /** Returns {@code true} if a restriction for {@code (promoCodeId, planId)} exists. */
    boolean existsByPromoCodeIdAndPlanId(UUID promoCodeId, UUID planId);

    /** Hard-deletes the restriction for {@code (promoCodeId, planId)}. */
    @Transactional
    void deleteByPromoCodeIdAndPlanId(UUID promoCodeId, UUID planId);

    /** Hard-deletes all restrictions for the given promo code. */
    @Transactional
    void deleteAllByPromoCodeId(UUID promoCodeId);
}
