package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.infrastructure.persistence.mapper.PromotionPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.PromotionJpaRepository;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link PromotionRepositoryPort} using
 * Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class PromotionRepositoryAdapter implements PromotionRepositoryPort {

    private final PromotionJpaRepository    jpaRepository;
    private final PromotionPersistenceMapper mapper;

    @Override
    public Promotion save(Promotion promotion) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(promotion)));
    }

    @Override
    public Optional<Promotion> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Promotion> findAll() {
        return mapper.toDomainList(jpaRepository.findAllByOrderByNameAsc());
    }

    @Override
    public List<Promotion> findByCampaignId(UUID campaignId) {
        return mapper.toDomainList(jpaRepository.findAllByCampaignIdOrderByNameAsc(campaignId));
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.PROMOTION_NOT_FOUND,
                "Promotion not found or already deleted: " + id);
        }
    }
}
