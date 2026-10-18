package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.PlatformInvoiceEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import com.company.bsmsvc.domain.model.InvoiceFilter;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.infrastructure.persistence.mapper.PlatformInvoiceEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.PlatformInvoiceJpaRepository;
import com.company.bsmsvc.infrastructure.persistence.specification.PlatformInvoiceSpecifications;
import com.company.bsmsvc.domain.exception.SubscriptionNotFoundException;
import jakarta.persistence.EntityManager;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlatformInvoiceRepositoryAdapter implements PlatformInvoiceRepositoryPort {

    private static final EnumSet<InvoiceSource> RECURRING_SOURCES =
        EnumSet.of(InvoiceSource.MANUAL, InvoiceSource.SUBSCRIPTION_RENEWAL);


    private final PlatformInvoiceJpaRepository platformInvoiceJpaRepository;
    private final PlatformInvoiceEntityMapper platformInvoiceEntityMapper;
    private final EntityManager entityManager;
    private final com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort billingLedgerRepositoryPort;

    @Override
    @Transactional
    public PlatformInvoice save(PlatformInvoice invoice) {
        PlatformInvoiceEntity entity = platformInvoiceEntityMapper.toEntity(invoice);
        SubscriptionEntity sub = entityManager.find(SubscriptionEntity.class, invoice.getSubscriptionId());
        if (sub == null) {
            throw new SubscriptionNotFoundException("Subscription with id " + invoice.getSubscriptionId() + " not found");
        }
        entity.setSubscription(sub);
        PlatformInvoiceEntity saved = platformInvoiceJpaRepository.save(entity);
        PlatformInvoice domain = platformInvoiceEntityMapper.toDomain(saved);

        // Emit ledger entries for domain events produced by the invoice
        for (Object ev : domain.pullDomainEvents()) {
            if (ev instanceof com.company.bsmsvc.domain.event.InvoiceCreatedEvent) {
                createLedgerForInvoice(domain, com.company.bsmsvc.domain.enums.LedgerEntryType.INVOICE_CREATED);
            } else if (ev instanceof com.company.bsmsvc.domain.event.InvoiceMarkedPaidEvent) {
                createLedgerForInvoice(domain, com.company.bsmsvc.domain.enums.LedgerEntryType.INVOICE_PAID);
            } else if (ev instanceof com.company.bsmsvc.domain.event.InvoiceVoidedEvent) {
                createLedgerForInvoice(domain, com.company.bsmsvc.domain.enums.LedgerEntryType.INVOICE_VOIDED);
            } else if (ev instanceof com.company.bsmsvc.domain.event.InvoiceRenewedEvent) {
                createLedgerForInvoice(domain, com.company.bsmsvc.domain.enums.LedgerEntryType.INVOICE_RENEWED);
            } else if (ev instanceof com.company.bsmsvc.domain.event.InvoicePdfGeneratedEvent) {
                createLedgerForInvoice(domain, com.company.bsmsvc.domain.enums.LedgerEntryType.INVOICE_PDF_GENERATED);
            }
        }

        return domain;
    }

    private void createLedgerForInvoice(PlatformInvoice invoice, com.company.bsmsvc.domain.enums.LedgerEntryType type) {
        com.company.bsmsvc.domain.model.BillingLedgerEntry entry = com.company.bsmsvc.domain.model.BillingLedgerEntry.builder()
            .id(java.util.UUID.randomUUID())
            .tenantId(invoice.getTenantId())
            .subscriptionId(invoice.getSubscriptionId())
            .invoiceId(invoice.getId())
            .creditNoteId(null)
            .entryType(type)
            .amountMinor(type == com.company.bsmsvc.domain.enums.LedgerEntryType.INVOICE_CREATED
                || type == com.company.bsmsvc.domain.enums.LedgerEntryType.INVOICE_RENEWED
                ? invoice.getAmountDue() : invoice.getAmountPaid())
            .currency(invoice.getCurrency())
            .description(type.name())
            .metadata(null)
            .createdAt(java.time.Instant.now())
            .build();
        billingLedgerRepositoryPort.save(entry);
    }

    @Override
    public Optional<PlatformInvoice> findById(UUID id) {
        return platformInvoiceJpaRepository.findById(id)
            .map(platformInvoiceEntityMapper::toDomain);
    }

    @Override
    public Optional<PlatformInvoice> findByInvoiceNumber(String invoiceNumber) {
        return platformInvoiceJpaRepository.findByInvoiceNumber(invoiceNumber)
            .map(platformInvoiceEntityMapper::toDomain);
    }

    @Override
    public List<PlatformInvoice> findByTenantId(UUID tenantId) {
        return platformInvoiceJpaRepository.findByTenantId(tenantId)
            .stream()
            .map(platformInvoiceEntityMapper::toDomain)
            .toList();
    }

    @Override
    public List<PlatformInvoice> findBySubscriptionId(UUID subscriptionId) {
        return platformInvoiceJpaRepository.findBySubscriptionId(subscriptionId)
            .stream()
            .map(platformInvoiceEntityMapper::toDomain)
            .toList();
    }

    @Override
    public boolean existsBySubscriptionIdAndPeriodStartAndPeriodEnd(UUID subscriptionId, java.time.Instant periodStart, java.time.Instant periodEnd) {
        return platformInvoiceJpaRepository.existsBySubscriptionIdAndPeriodStartAndPeriodEnd(subscriptionId, periodStart, periodEnd);
    }

    @Override
    public boolean existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(UUID subscriptionId, java.time.Instant periodStart, java.time.Instant periodEnd) {
        return platformInvoiceJpaRepository.existsBySubscriptionIdAndPeriodStartAndPeriodEndAndSourceIn(
            subscriptionId, periodStart, periodEnd, RECURRING_SOURCES);
    }

    @Override
    public PageResult<PlatformInvoice> findInvoices(InvoiceFilter filter, int page, int size, String sortBy, String sortDirection) {
        Sort.Direction direction = Sort.Direction.fromOptionalString(sortDirection).orElse(Sort.Direction.DESC);
        String normalizedSortBy = sortBy == null || sortBy.isBlank() ? "createdAt" : sortBy;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, normalizedSortBy));
        Page<PlatformInvoiceEntity> invoicePage = platformInvoiceJpaRepository.findAll(
            PlatformInvoiceSpecifications.fromFilter(filter),
            pageRequest
        );
        return new PageResult<>(
            invoicePage.stream().map(platformInvoiceEntityMapper::toDomain).toList(),
            invoicePage.getNumber(),
            invoicePage.getSize(),
            invoicePage.getTotalElements(),
            invoicePage.getTotalPages(),
            invoicePage.hasNext()
        );
    }
}
