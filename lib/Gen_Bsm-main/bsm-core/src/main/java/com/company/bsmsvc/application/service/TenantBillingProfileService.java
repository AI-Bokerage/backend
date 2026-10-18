package com.company.bsmsvc.application.service;

import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import java.util.UUID;

/**
 * Manages a tenant's billing profile — payment provider, currency, and external customer id.
 * The anchor service for the tenant-billing aggregate; auto-configured by the starter whenever
 * {@link com.company.bsmsvc.domain.port.TenantBillingProfileRepositoryPort} is present.
 */
public interface TenantBillingProfileService {
    TenantBillingProfile createProfile(UUID tenantId, PaymentProvider provider, String externalCustomerId, String currency);
    TenantBillingProfile getProfile(UUID tenantId);
    TenantBillingProfile updateProvider(UUID tenantId, PaymentProvider newProvider);
    TenantBillingProfile updateCurrency(UUID tenantId, String newCurrency);
    TenantBillingProfile assignExternalCustomerId(UUID tenantId, String externalCustomerId);
}
