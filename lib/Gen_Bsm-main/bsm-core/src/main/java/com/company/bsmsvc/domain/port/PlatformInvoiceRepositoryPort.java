package com.company.bsmsvc.domain.port;

import com.company.bsmsvc.domain.model.InvoiceFilter;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence/query port for platform invoices, including duplicate-period checks used to
 * prevent double-billing a subscription's period. Implementations must be
 * thread-safe/stateless.
 */
public interface PlatformInvoiceRepositoryPort {

    PlatformInvoice save(PlatformInvoice invoice);

    Optional<PlatformInvoice> findById(UUID id);

    Optional<PlatformInvoice> findByInvoiceNumber(String invoiceNumber);

    boolean existsBySubscriptionIdAndPeriodStartAndPeriodEnd(UUID subscriptionId, Instant periodStart, Instant periodEnd);

    /**
     * Returns true if a RECURRING invoice (source MANUAL or SUBSCRIPTION_RENEWAL) already exists
     * for the given subscription and billing period.  Non-recurring sources are not considered.
     */
    boolean existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(UUID subscriptionId, Instant periodStart, Instant periodEnd);

    List<PlatformInvoice> findByTenantId(UUID tenantId);

    List<PlatformInvoice> findBySubscriptionId(UUID subscriptionId);

    PageResult<PlatformInvoice> findInvoices(
        InvoiceFilter filter,
        int page,
        int size,
        String sortBy,
        String sortDirection
    );
}
