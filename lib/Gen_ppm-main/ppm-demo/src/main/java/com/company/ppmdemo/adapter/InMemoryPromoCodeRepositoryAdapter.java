package com.company.ppmdemo.adapter;

import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory {@link PromoCodeRepositoryPort} implementation — see InMemoryPlanRepositoryAdapter. */
@Component
public class InMemoryPromoCodeRepositoryAdapter implements PromoCodeRepositoryPort {

    private final Map<UUID, PromoCode> store = new ConcurrentHashMap<>();

    @Override
    public PromoCode save(PromoCode promoCode) {
        PromoCode withVersion = PromoCode.builder()
            .id(promoCode.getId()).version(promoCode.getVersion() == null ? 0L : promoCode.getVersion() + 1)
            .code(promoCode.getCode()).discountType(promoCode.getDiscountType()).value(promoCode.getValue())
            .validFrom(promoCode.getValidFrom()).validUntil(promoCode.getValidUntil())
            .usageCap(promoCode.getUsageCap()).usageCount(promoCode.getUsageCount())
            .firstTimeOnly(promoCode.getFirstTimeOnly()).active(promoCode.getActive())
            .createdAt(promoCode.getCreatedAt() == null ? Instant.now() : promoCode.getCreatedAt())
            .updatedAt(Instant.now())
            .createdBy(promoCode.getCreatedBy()).updatedBy(promoCode.getUpdatedBy())
            .build();
        store.put(withVersion.getId(), withVersion);
        return withVersion;
    }

    @Override
    public Optional<PromoCode> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<PromoCode> findAll() {
        return store.values().stream().sorted((a, b) -> a.getCode().compareTo(b.getCode())).toList();
    }

    @Override
    public Optional<PromoCode> findByCode(String code) {
        return store.values().stream().filter(p -> p.getCode().equals(code)).findFirst();
    }

    @Override
    public boolean existsByCode(String code) {
        return store.values().stream().anyMatch(p -> p.getCode().equals(code));
    }

    @Override
    public void softDelete(UUID id, UUID actorId) {
        PromoCode existing = store.get(id);
        if (existing == null) {
            throw new ResourceNotFoundException(ErrorCode.PROMO_CODE_NOT_FOUND, "Promo code not found: " + id);
        }
        store.put(id, PromoCode.builder()
            .id(existing.getId()).version(existing.getVersion() + 1)
            .code(existing.getCode()).discountType(existing.getDiscountType()).value(existing.getValue())
            .validFrom(existing.getValidFrom()).validUntil(existing.getValidUntil())
            .usageCap(existing.getUsageCap()).usageCount(existing.getUsageCount())
            .firstTimeOnly(existing.getFirstTimeOnly()).active(false)
            .createdAt(existing.getCreatedAt()).updatedAt(Instant.now())
            .createdBy(existing.getCreatedBy()).updatedBy(actorId)
            .build());
    }
}
