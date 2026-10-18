package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.event.TenantBillingProfileCreatedEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.port.TenantBillingProfileRepositoryPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantBillingProfileServiceImpl implements TenantBillingProfileService {

    private final TenantBillingProfileRepositoryPort repository;
    private final TenantScopePort tenantScopeEnforcer;

    public TenantBillingProfileServiceImpl(TenantBillingProfileRepositoryPort repository,
                                           TenantScopePort tenantScopeEnforcer) {
        this.repository = repository;
        this.tenantScopeEnforcer = tenantScopeEnforcer;
    }

    @Override
    @Transactional
    public TenantBillingProfile createProfile(UUID tenantId, PaymentProvider provider, String externalCustomerId, String currency) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        repository.findByTenantId(tenantId).ifPresent(existing -> {
            throw new BusinessRuleViolationException(
                "Billing profile already exists for tenant: " + tenantId);
        });
        TenantBillingProfile profile = TenantBillingProfile.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .paymentProvider(provider)
            .externalCustomerId(externalCustomerId)
            .currency(currency)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        profile.registerEvent(new TenantBillingProfileCreatedEvent(
            profile.getId(), profile.getTenantId(), profile.getPaymentProvider(), Instant.now()
        ));
        return repository.save(profile);
    }

    @Override
    public TenantBillingProfile getProfile(UUID tenantId) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        return repository.findByTenantId(tenantId)
            .orElseThrow(() -> new TenantBillingProfileNotFoundException(
                "Billing profile not found for tenant: " + tenantId));
    }

    @Override
    @Transactional
    public TenantBillingProfile updateProvider(UUID tenantId, PaymentProvider newProvider) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        TenantBillingProfile profile = repository.findByTenantId(tenantId)
            .orElseThrow(() -> new TenantBillingProfileNotFoundException(
                "Billing profile not found for tenant: " + tenantId));
        profile.updateProvider(newProvider);
        return repository.save(profile);
    }

    @Override
    @Transactional
    public TenantBillingProfile updateCurrency(UUID tenantId, String newCurrency) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        TenantBillingProfile profile = repository.findByTenantId(tenantId)
            .orElseThrow(() -> new TenantBillingProfileNotFoundException(
                "Billing profile not found for tenant: " + tenantId));
        profile.updateCurrency(newCurrency);
        return repository.save(profile);
    }

    @Override
    @Transactional
    public TenantBillingProfile assignExternalCustomerId(UUID tenantId, String externalCustomerId) {
        // Internal method called by PaymentMethodServiceImpl — skip JWT assertion here.
        // The calling service already asserts tenant access on the public entry point.
        TenantBillingProfile profile = repository.findByTenantId(tenantId)
            .orElseThrow(() -> new TenantBillingProfileNotFoundException(
                "Billing profile not found for tenant: " + tenantId));
        profile.assignExternalCustomerId(externalCustomerId);
        return repository.save(profile);
    }
}
