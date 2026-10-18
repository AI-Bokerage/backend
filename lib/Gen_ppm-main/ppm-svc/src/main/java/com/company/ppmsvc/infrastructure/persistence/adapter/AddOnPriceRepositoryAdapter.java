package com.company.ppmsvc.infrastructure.persistence.adapter;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.addonprice.model.AddOnPrice;
import com.company.ppmsvc.addonprice.port.AddOnPriceRepositoryPort;
import com.company.ppmsvc.infrastructure.persistence.mapper.AddOnPricePersistenceMapper;
import com.company.ppmsvc.infrastructure.persistence.repository.AddOnPriceJpaRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter that implements {@link AddOnPriceRepositoryPort} using
 * Spring Data JPA.
 *
 * <p>This class is the only place in the infrastructure layer that knows
 * about {@link com.company.ppmsvc.infrastructure.persistence.entity.AddOnPriceEntity}.
 * All callers above this boundary (application services) interact exclusively
 * with {@link AddOnPrice} domain objects via the port.
 */
@Component
@RequiredArgsConstructor
public class AddOnPriceRepositoryAdapter implements AddOnPriceRepositoryPort {

    private final AddOnPriceJpaRepository     jpaRepository;
    private final AddOnPricePersistenceMapper  mapper;

    @Override
    public AddOnPrice save(AddOnPrice price) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(price)));
    }

    @Override
    public Optional<AddOnPrice> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<AddOnPrice> findAll() {
        return mapper.toDomainList(jpaRepository.findAllByOrderByAddOnIdAsc());
    }

    @Override
    public List<AddOnPrice> findByAddOnId(UUID addOnId) {
        return mapper.toDomainList(jpaRepository.findByAddOnId(addOnId));
    }

    @Override
    public List<AddOnPrice> findByAddOnIdAndRegionAndCurrency(
            UUID addOnId, String region, String currency) {
        return mapper.toDomainList(
            jpaRepository.findByAddOnIdAndRegionAndCurrency(addOnId, region, currency));
    }

    @Override
    public boolean exists(UUID addOnId, String region, String currency,
                          BillingCycle cycle, LocalDate effectiveFrom) {
        return jpaRepository.existsByAddOnIdAndRegionAndCurrencyAndCycleAndEffectiveFrom(
            addOnId, region, currency, cycle, effectiveFrom);
    }

    @Override
    public Optional<AddOnPrice> findActivePrice(UUID addOnId, String region, String currency, BillingCycle cycle) {
        return jpaRepository
            .findFirstByAddOnIdAndRegionAndCurrencyAndCycleAndActiveTrueAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                addOnId, region, currency, cycle, LocalDate.now())
            .map(mapper::toDomain);
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        int updated = jpaRepository.softDeleteById(id, Instant.now(), actorId);
        if (updated == 0) {
            throw new ResourceNotFoundException(ErrorCode.ADD_ON_PRICE_NOT_FOUND,
                "Add-on price not found or already deleted: " + id);
        }
    }
}
