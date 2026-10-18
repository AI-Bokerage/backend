package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.event.PaymentMethodAddedEvent;
import com.company.bsmsvc.domain.event.PaymentMethodDefaultChangedEvent;
import com.company.bsmsvc.domain.event.PaymentMethodRemovedEvent;
import com.company.bsmsvc.domain.model.PaymentMethod;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.PaymentMethodEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.PaymentMethodEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.PaymentMethodJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentMethodRepositoryAdapter implements PaymentMethodRepositoryPort {

    private final PaymentMethodJpaRepository jpaRepository;
    private final PaymentMethodEntityMapper mapper;

    @Override
    @Transactional
    public PaymentMethod save(PaymentMethod paymentMethod) {
        PaymentMethodEntity entity = mapper.toEntity(paymentMethod);
        PaymentMethodEntity saved = jpaRepository.save(entity);
        PaymentMethod domain = mapper.toDomain(saved);

        for (Object ev : paymentMethod.pullDomainEvents()) {
            if (ev instanceof PaymentMethodAddedEvent e) {
                log.debug("PAYMENT_METHOD_ADDED id={} tenantId={} provider={}", e.paymentMethodId(), e.tenantId(), e.paymentProvider());
            } else if (ev instanceof PaymentMethodRemovedEvent e) {
                log.debug("PAYMENT_METHOD_REMOVED id={} tenantId={}", e.paymentMethodId(), e.tenantId());
            } else if (ev instanceof PaymentMethodDefaultChangedEvent e) {
                log.debug("PAYMENT_METHOD_DEFAULT_CHANGED id={} tenantId={}", e.paymentMethodId(), e.tenantId());
            }
        }

        return domain;
    }

    @Override
    public Optional<PaymentMethod> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<PaymentMethod> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantId(tenantId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsByTenantIdAndExternalPaymentMethodId(UUID tenantId, String externalPaymentMethodId) {
        return jpaRepository.existsByTenantIdAndExternalPaymentMethodId(tenantId, externalPaymentMethodId);
    }

    @Override
    @Transactional
    public void unsetDefaultForTenant(UUID tenantId) {
        jpaRepository.unsetDefaultForTenant(tenantId);
    }

    @Override
    public Optional<PaymentMethod> findDefaultByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantIdAndIsDefaultTrue(tenantId).map(mapper::toDomain);
    }
}
