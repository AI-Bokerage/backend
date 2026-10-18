package com.company.bsmsvc.infrastructure.persistence.adapter;

import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.domain.event.CreditNoteAppliedEvent;
import com.company.bsmsvc.domain.event.CreditNoteCreatedEvent;
import com.company.bsmsvc.domain.event.CreditNoteVoidedEvent;
import com.company.bsmsvc.domain.model.BillingLedgerEntry;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.CreditNoteRepositoryPort;
import com.company.bsmsvc.domain.enums.LedgerEntryType;
import com.company.bsmsvc.infrastructure.persistence.entity.CreditNoteEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.CreditNoteEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.CreditNoteJpaRepository;
import com.company.bsmsvc.infrastructure.persistence.specification.CreditNoteSpecifications;
import java.time.Instant;
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
public class CreditNoteRepositoryAdapter implements CreditNoteRepositoryPort {

    private final CreditNoteJpaRepository creditNoteJpaRepository;
    private final CreditNoteEntityMapper creditNoteEntityMapper;
    private final BillingLedgerRepositoryPort billingLedgerRepositoryPort;

    @Override
    @Transactional
    public CreditNote save(CreditNote creditNote) {
        CreditNoteEntity entity = creditNoteEntityMapper.toEntity(creditNote);
        CreditNoteEntity saved = creditNoteJpaRepository.save(entity);
        CreditNote domain = creditNoteEntityMapper.toDomain(saved);

        for (Object ev : creditNote.pullDomainEvents()) {
            if (ev instanceof CreditNoteCreatedEvent) {
                saveLedgerEntry(domain, LedgerEntryType.CREDIT_NOTE_CREATED,
                    domain.getAmountMinor(), "Credit note created: " + domain.getCreditNumber());
            } else if (ev instanceof CreditNoteAppliedEvent) {
                saveLedgerEntry(domain, LedgerEntryType.CREDIT_APPLIED,
                    -Math.abs(domain.getAmountMinor()), "Credit applied: " + domain.getCreditNumber());
            } else if (ev instanceof CreditNoteVoidedEvent) {
                saveLedgerEntry(domain, LedgerEntryType.ADJUSTMENT,
                    0L, "Credit note voided: " + domain.getCreditNumber());
            }
        }

        return domain;
    }

    @Override
    public Optional<CreditNote> findById(UUID id) {
        return creditNoteJpaRepository.findById(id).map(creditNoteEntityMapper::toDomain);
    }

    @Override
    public PageResult<CreditNote> findCreditNotes(CreditNoteFilter filter, int page, int size,
                                                   String sortBy, String sortDirection) {
        Sort.Direction direction = Sort.Direction.fromOptionalString(sortDirection).orElse(Sort.Direction.DESC);
        String normalizedSortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, normalizedSortBy));
        Page<CreditNoteEntity> result = creditNoteJpaRepository.findAll(
            CreditNoteSpecifications.fromFilter(filter), pageRequest
        );
        return new PageResult<>(
            result.stream().map(creditNoteEntityMapper::toDomain).toList(),
            result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages(), result.hasNext()
        );
    }

    private void saveLedgerEntry(CreditNote creditNote, LedgerEntryType type, long amountMinor, String description) {
        billingLedgerRepositoryPort.save(BillingLedgerEntry.builder()
            .id(UUID.randomUUID())
            .tenantId(creditNote.getTenantId())
            .invoiceId(creditNote.getInvoiceId())
            .creditNoteId(creditNote.getId())
            .entryType(type)
            .amountMinor(amountMinor)
            .currency(creditNote.getCurrency())
            .description(description)
            .createdAt(Instant.now())
            .build());
    }
}
