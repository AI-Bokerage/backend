package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.event.CustomerCreatedEvent;
import com.company.bsmsvc.domain.event.TenantBillingCurrencyChangedEvent;
import com.company.bsmsvc.domain.event.TenantBillingProfileCreatedEvent;
import com.company.bsmsvc.domain.event.TenantBillingProviderChangedEvent;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.port.TenantBillingProfileRepositoryPort;
import com.company.bsmsvc.infrastructure.outbox.BsmAuditEventPublisher;
import com.company.bsmsvc.infrastructure.persistence.entity.TenantBillingProfileEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.TenantBillingProfileEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.TenantBillingProfileJpaRepository;
import java.util.Map;
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
public class TenantBillingProfileRepositoryAdapter implements TenantBillingProfileRepositoryPort {

    private final TenantBillingProfileJpaRepository jpaRepository;
    private final TenantBillingProfileEntityMapper mapper;
    private final BsmAuditEventPublisher auditEventPublisher;

    @Override
    @Transactional
    public TenantBillingProfile save(TenantBillingProfile profile) {
        TenantBillingProfileEntity entity = mapper.toEntity(profile);
        TenantBillingProfileEntity saved = jpaRepository.save(entity);
        TenantBillingProfile domain = mapper.toDomain(saved);

        // Producer-conformance pass (docs/audit/17): these 4 domain events were already
        // registered by the domain model on every write but previously only log.debug()'d
        // (TenantBillingCurrencyChangedEvent wasn't even logged) instead of reaching the
        // cross-service outbox. No actor is available at this repository layer — none of
        // the 4 call sites (TenantBillingProfileServiceImpl, PaymentMethodServiceImpl) thread
        // a caller identity down this far — so actorId is null, not fabricated.
        for (Object ev : profile.pullDomainEvents()) {
            if (ev instanceof TenantBillingProfileCreatedEvent e) {
                log.debug("BILLING_PROFILE_CREATED profileId={} tenantId={} provider={}",
                    e.profileId(), e.tenantId(), e.paymentProvider());
                auditEventPublisher.publish("bsm.billing_profile.created", e.tenantId(),
                    "TenantBillingProfile", e.profileId(), null,
                    Map.of("provider", e.paymentProvider().name()));
            } else if (ev instanceof TenantBillingProviderChangedEvent e) {
                log.debug("BILLING_PROVIDER_CHANGED profileId={} tenantId={} from={} to={}",
                    e.profileId(), e.tenantId(), e.oldProvider(), e.newProvider());
                auditEventPublisher.publish("bsm.billing_profile.provider_changed", e.tenantId(),
                    "TenantBillingProfile", e.profileId(), null,
                    Map.of("oldProvider", e.oldProvider().name(), "newProvider", e.newProvider().name()));
            } else if (ev instanceof TenantBillingCurrencyChangedEvent e) {
                log.debug("BILLING_CURRENCY_CHANGED profileId={} tenantId={} from={} to={}",
                    e.profileId(), e.tenantId(), e.oldCurrency(), e.newCurrency());
                auditEventPublisher.publish("bsm.billing_profile.currency_changed", e.tenantId(),
                    "TenantBillingProfile", e.profileId(), null,
                    Map.of("oldCurrency", e.oldCurrency(), "newCurrency", e.newCurrency()));
            } else if (ev instanceof CustomerCreatedEvent e) {
                log.debug("CUSTOMER_CREATED profileId={} tenantId={} externalCustomerId={}",
                    e.profileId(), e.tenantId(), e.externalCustomerId());
                auditEventPublisher.publish("bsm.billing_profile.customer_created", e.tenantId(),
                    "TenantBillingProfile", e.profileId(), null,
                    Map.of("provider", e.paymentProvider().name()));
            }
        }

        return domain;
    }

    @Override
    public Optional<TenantBillingProfile> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantId(tenantId).map(mapper::toDomain);
    }
}
