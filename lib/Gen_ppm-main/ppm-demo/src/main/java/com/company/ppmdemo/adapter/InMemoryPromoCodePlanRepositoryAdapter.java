package com.company.ppmdemo.adapter;

import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import com.company.ppmsvc.promocodeplan.port.PromoCodePlanRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory {@link PromoCodePlanRepositoryPort} implementation — see InMemoryPlanRepositoryAdapter. */
@Component
public class InMemoryPromoCodePlanRepositoryAdapter implements PromoCodePlanRepositoryPort {

    private final Map<UUID, PromoCodePlan> store = new ConcurrentHashMap<>();

    @Override
    public PromoCodePlan save(PromoCodePlan mapping) {
        PromoCodePlan saved = PromoCodePlan.builder()
            .id(mapping.getId())
            .promoCodeId(mapping.getPromoCodeId()).planId(mapping.getPlanId())
            .createdAt(Instant.now()).createdBy(mapping.getCreatedBy())
            .build();
        store.put(saved.getId(), saved);
        return saved;
    }

    @Override
    public List<PromoCodePlan> saveAll(List<PromoCodePlan> mappings) {
        return mappings.stream().map(this::save).toList();
    }

    @Override
    public List<PromoCodePlan> findByPromoCodeId(UUID promoCodeId) {
        return store.values().stream().filter(m -> m.getPromoCodeId().equals(promoCodeId)).toList();
    }

    @Override
    public List<PromoCodePlan> findByPlanId(UUID planId) {
        return store.values().stream().filter(m -> m.getPlanId().equals(planId)).toList();
    }

    @Override
    public boolean exists(UUID promoCodeId, UUID planId) {
        return store.values().stream()
            .anyMatch(m -> m.getPromoCodeId().equals(promoCodeId) && m.getPlanId().equals(planId));
    }

    @Override
    public void delete(UUID promoCodeId, UUID planId) {
        store.values().removeIf(m -> m.getPromoCodeId().equals(promoCodeId) && m.getPlanId().equals(planId));
    }

    @Override
    public void deleteAllByPromoCodeId(UUID promoCodeId) {
        store.values().removeIf(m -> m.getPromoCodeId().equals(promoCodeId));
    }
}
