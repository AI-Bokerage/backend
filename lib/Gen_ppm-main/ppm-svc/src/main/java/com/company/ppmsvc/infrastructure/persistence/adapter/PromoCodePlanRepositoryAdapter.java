package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import com.company.ppmsvc.promocodeplan.port.PromoCodePlanRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.PromoCodePlanPersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.PromoCodePlanJpaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link PromoCodePlanRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows about
 * {@link com.company.ppmsvc.infrastructure.persistence.entity.PromoCodePlanEntity}.
 * All callers above this boundary interact exclusively with {@link PromoCodePlan}
 * domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class PromoCodePlanRepositoryAdapter implements PromoCodePlanRepositoryPort {

    private final PromoCodePlanJpaRepository     jpaRepository;
    private final PromoCodePlanPersistenceMapper mapper;

    @Override
    public PromoCodePlan save(PromoCodePlan mapping) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(mapping)));
    }

    @Override
    public List<PromoCodePlan> saveAll(List<PromoCodePlan> mappings) {
        List<com.company.ppmsvc.infrastructure.persistence.entity.PromoCodePlanEntity> entities =
            mappings.stream().map(mapper::toEntity).toList();
        return mapper.toDomainList(jpaRepository.saveAll(entities));
    }

    @Override
    public List<PromoCodePlan> findByPromoCodeId(UUID promoCodeId) {
        return mapper.toDomainList(
            jpaRepository.findByPromoCodeIdOrderByCreatedAtAsc(promoCodeId));
    }

    @Override
    public List<PromoCodePlan> findByPlanId(UUID planId) {
        return mapper.toDomainList(jpaRepository.findByPlanId(planId));
    }

    @Override
    public boolean exists(UUID promoCodeId, UUID planId) {
        return jpaRepository.existsByPromoCodeIdAndPlanId(promoCodeId, planId);
    }

    @Override
    public void delete(UUID promoCodeId, UUID planId) {
        jpaRepository.deleteByPromoCodeIdAndPlanId(promoCodeId, planId);
    }

    @Override
    public void deleteAllByPromoCodeId(UUID promoCodeId) {
        jpaRepository.deleteAllByPromoCodeId(promoCodeId);
    }
}
