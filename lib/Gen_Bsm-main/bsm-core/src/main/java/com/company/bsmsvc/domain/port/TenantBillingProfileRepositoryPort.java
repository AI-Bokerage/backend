package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.TenantBillingProfile;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for a tenant's billing profile (payment provider, currency, external
 * customer id). Implementations must be thread-safe/stateless.
 */
public interface TenantBillingProfileRepositoryPort {
    TenantBillingProfile save(TenantBillingProfile profile);
    Optional<TenantBillingProfile> findByTenantId(UUID tenantId);
}
