package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.infrastructure.persistence.mapper.PromotionRedemptionPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.PromotionRedemptionJpaRepository;
import com.company.ppmsvc.promotionredemption.model.PromotionRedemption;
import com.company.ppmsvc.promotionredemption.port.PromotionRedemptionRepositoryPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link PromotionRedemptionRepositoryPort}
 * using Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class PromotionRedemptionRepositoryAdapter implements PromotionRedemptionRepositoryPort {

    private final PromotionRedemptionJpaRepository    jpaRepository;
    private final PromotionRedemptionPersistenceMapper mapper;

    @Override
    public PromotionRedemption save(PromotionRedemption redemption) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(redemption)));
    }

    @Override
    public int countByPromotionIdAndCustomerId(UUID promotionId, String customerId) {
        return jpaRepository.countByPromotionIdAndCustomerId(promotionId, customerId);
    }
}
