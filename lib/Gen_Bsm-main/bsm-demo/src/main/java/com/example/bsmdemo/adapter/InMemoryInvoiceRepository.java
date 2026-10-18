package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.model.InvoiceFilter;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryInvoiceRepository implements PlatformInvoiceRepositoryPort {

    private final ConcurrentHashMap<UUID, PlatformInvoice> store = new ConcurrentHashMap<>();

    @Override
    public PlatformInvoice save(PlatformInvoice invoice) {
        if (invoice.getId() == null) {
            invoice = invoice.toBuilder().id(UUID.randomUUID()).build();
        }
        store.put(invoice.getId(), invoice);
        return invoice;
    }

    @Override
    public Optional<PlatformInvoice> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<PlatformInvoice> findByInvoiceNumber(String invoiceNumber) {
        return store.values().stream()
            .filter(i -> invoiceNumber.equals(i.getInvoiceNumber()))
            .findFirst();
    }

    @Override
    public boolean existsBySubscriptionIdAndPeriodStartAndPeriodEnd(UUID subscriptionId, Instant periodStart, Instant periodEnd) {
        return store.values().stream()
            .anyMatch(i -> subscriptionId.equals(i.getSubscriptionId())
                && periodStart.equals(i.getPeriodStart())
                && periodEnd.equals(i.getPeriodEnd()));
    }

    @Override
    public boolean existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(UUID subscriptionId, Instant periodStart, Instant periodEnd) {
        return store.values().stream()
            .anyMatch(i -> subscriptionId.equals(i.getSubscriptionId())
                && periodStart.equals(i.getPeriodStart())
                && periodEnd.equals(i.getPeriodEnd())
                && (i.getSource() == InvoiceSource.MANUAL || i.getSource() == InvoiceSource.SUBSCRIPTION_RENEWAL));
    }

    @Override
    public List<PlatformInvoice> findByTenantId(UUID tenantId) {
        return store.values().stream()
            .filter(i -> tenantId.equals(i.getTenantId()))
            .toList();
    }

    @Override
    public List<PlatformInvoice> findBySubscriptionId(UUID subscriptionId) {
        return store.values().stream()
            .filter(i -> subscriptionId.equals(i.getSubscriptionId()))
            .toList();
    }

    @Override
    public PageResult<PlatformInvoice> findInvoices(InvoiceFilter filter, int page, int size, String sortBy, String sortDirection) {
        List<PlatformInvoice> matched = store.values().stream()
            .filter(i -> filter == null || filter.tenantId() == null || filter.tenantId().equals(i.getTenantId()))
            .filter(i -> filter == null || filter.subscriptionId() == null || filter.subscriptionId().equals(i.getSubscriptionId()))
            .filter(i -> filter == null || filter.invoiceNumber() == null || filter.invoiceNumber().equals(i.getInvoiceNumber()))
            .filter(i -> filter == null || filter.status() == null || filter.status().equals(i.getStatus()))
            .toList();

        int fromIndex = Math.min(page * size, matched.size());
        int toIndex = Math.min(fromIndex + size, matched.size());
        List<PlatformInvoice> content = matched.subList(fromIndex, toIndex);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) matched.size() / size);
        return new PageResult<>(content, page, size, matched.size(), totalPages, toIndex < matched.size());
    }
}
