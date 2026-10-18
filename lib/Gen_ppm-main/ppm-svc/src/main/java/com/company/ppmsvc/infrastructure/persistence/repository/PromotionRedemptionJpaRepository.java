package com.company.ppmsvc.infrastructure.persistence.repository;

import com.company.ppmsvc.infrastructure.persistence.entity.PromotionRedemptionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link PromotionRedemptionEntity}.
 *
 * <p>Application services must not use this directly — they depend on
 * {@link com.company.ppmsvc.promotionredemption.port.PromotionRedemptionRepositoryPort}.
 */
public interface PromotionRedemptionJpaRepository extends JpaRepository<PromotionRedemptionEntity, UUID> {

    /** Counts how many times {@code customerId} has redeemed {@code promotionId}. */
    int countByPromotionIdAndCustomerId(UUID promotionId, String customerId);
}
