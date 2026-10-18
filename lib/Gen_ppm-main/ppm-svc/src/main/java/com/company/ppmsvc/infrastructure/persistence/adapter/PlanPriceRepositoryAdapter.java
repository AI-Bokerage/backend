package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.PlanPricePersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.PlanPriceJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link PlanPriceRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows
 * about {@link com.company.ppmsvc.infrastructure.persistence.entity.PlanPriceEntity}.
 * All callers above this boundary (application services) interact exclusively
 * with {@link PlanPrice} domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class PlanPriceRepositoryAdapter implements PlanPriceRepositoryPort {

    private final PlanPriceJpaRepository     jpaRepository;
    private final PlanPricePersistenceMapper  mapper;

    @Override
    public PlanPrice save(PlanPrice price) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(price)));
    }

    @Override
    public Optional<PlanPrice> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<PlanPrice> findAll() {
        return mapper.toDomainList(jpaRepository.findAllByOrderByPlanIdAsc());
    }

    @Override
    public List<PlanPrice> findByPlanId(UUID planId) {
        return mapper.toDomainList(jpaRepository.findByPlanId(planId));
    }

    @Override
    public List<PlanPrice> findByPlanIdAndRegionAndCurrency(
            UUID planId, String region, String currency) {
        return mapper.toDomainList(
            jpaRepository.findByPlanIdAndRegionAndCurrency(planId, region, currency));
    }

    @Override
    public boolean exists(UUID planId, String region, String currency,
                          BillingCycle cycle, LocalDate effectiveFrom) {
        return jpaRepository.existsByPlanIdAndRegionAndCurrencyAndCycleAndEffectiveFrom(
            planId, region, currency, cycle, effectiveFrom);
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.PLAN_PRICE_NOT_FOUND,
                "Plan price not found or already deleted: " + id);
        }
    }
}
