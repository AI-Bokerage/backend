package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.PaymentMethod;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryPaymentMethodRepository implements PaymentMethodRepositoryPort {

    private final ConcurrentHashMap<UUID, PaymentMethod> store = new ConcurrentHashMap<>();

    @Override
    public PaymentMethod save(PaymentMethod paymentMethod) {
        if (paymentMethod.getId() == null) {
            paymentMethod = paymentMethod.toBuilder().id(UUID.randomUUID()).build();
        }
        store.put(paymentMethod.getId(), paymentMethod);
        return paymentMethod;
    }

    @Override
    public Optional<PaymentMethod> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<PaymentMethod> findByTenantId(UUID tenantId) {
        return store.values().stream()
            .filter(pm -> tenantId.equals(pm.getTenantId()))
            .toList();
    }

    @Override
    public Optional<PaymentMethod> findDefaultByTenantId(UUID tenantId) {
        return store.values().stream()
            .filter(pm -> tenantId.equals(pm.getTenantId()))
            .filter(PaymentMethod::isDefault)
            .findFirst();
    }

    @Override
    public boolean existsByTenantIdAndExternalPaymentMethodId(UUID tenantId, String externalPaymentMethodId) {
        return store.values().stream()
            .anyMatch(pm -> tenantId.equals(pm.getTenantId()) && externalPaymentMethodId.equals(pm.getExternalPaymentMethodId()));
    }

    @Override
    public void unsetDefaultForTenant(UUID tenantId) {
        store.values().stream()
            .filter(pm -> tenantId.equals(pm.getTenantId()))
            .filter(PaymentMethod::isDefault)
            .forEach(pm -> store.put(pm.getId(), pm.toBuilder().isDefault(false).build()));
    }
}
