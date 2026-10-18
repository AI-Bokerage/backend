package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.PaymentMethod;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for a tenant's saved payment methods. Implementations must be
 * thread-safe/stateless.
 */
public interface PaymentMethodRepositoryPort {
    PaymentMethod save(PaymentMethod paymentMethod);
    Optional<PaymentMethod> findById(UUID id);
    List<PaymentMethod> findByTenantId(UUID tenantId);
    Optional<PaymentMethod> findDefaultByTenantId(UUID tenantId);
    boolean existsByTenantIdAndExternalPaymentMethodId(UUID tenantId, String externalPaymentMethodId);
    void unsetDefaultForTenant(UUID tenantId);
}
