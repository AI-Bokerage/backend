package com.company.bsmsvc.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.domain.enums.LedgerEntryType;
import com.company.bsmsvc.domain.event.CreditNoteCreatedEvent;
import com.company.bsmsvc.domain.model.BillingLedgerEntry;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.model.PageResult;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.infrastructure.persistence.entity.CreditNoteEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.CreditNoteEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.CreditNoteJpaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class CreditNoteRepositoryAdapterTest {

    @Mock private CreditNoteJpaRepository creditNoteJpaRepository;
    @Mock private CreditNoteEntityMapper creditNoteEntityMapper;
    @Mock private BillingLedgerRepositoryPort billingLedgerRepositoryPort;
    @InjectMocks private CreditNoteRepositoryAdapter adapter;

    @Test
    void save_emitsLedgerEntry_whenCreatedEventPresent() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CreditNote cn = openCreditNote(id, tenantId);
        cn.registerEvent(new CreditNoteCreatedEvent(id, tenantId, UUID.randomUUID(), "CN-001", 1000L, Instant.now()));

        CreditNoteEntity entity = CreditNoteEntity.builder().id(id).build();
        when(creditNoteEntityMapper.toEntity(cn)).thenReturn(entity);
        when(creditNoteJpaRepository.save(entity)).thenReturn(entity);
        when(creditNoteEntityMapper.toDomain(entity)).thenReturn(cn);
        when(billingLedgerRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));

        adapter.save(cn);

        ArgumentCaptor<BillingLedgerEntry> captor = ArgumentCaptor.forClass(BillingLedgerEntry.class);
        verify(billingLedgerRepositoryPort).save(captor.capture());
        assertThat(captor.getValue().getEntryType()).isEqualTo(LedgerEntryType.CREDIT_NOTE_CREATED);
    }

    @Test
    void save_noDomainEvents_noLedgerEntry() {
        UUID id = UUID.randomUUID();
        CreditNote cn = openCreditNote(id, UUID.randomUUID());

        CreditNoteEntity entity = CreditNoteEntity.builder().id(id).build();
        when(creditNoteEntityMapper.toEntity(cn)).thenReturn(entity);
        when(creditNoteJpaRepository.save(entity)).thenReturn(entity);
        when(creditNoteEntityMapper.toDomain(entity)).thenReturn(cn);

        adapter.save(cn);

        verify(billingLedgerRepositoryPort, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(creditNoteJpaRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void findById_returnsMapped_whenFound() {
        UUID id = UUID.randomUUID();
        CreditNoteEntity entity = CreditNoteEntity.builder().id(id).build();
        CreditNote domain = openCreditNote(id, UUID.randomUUID());
        when(creditNoteJpaRepository.findById(id)).thenReturn(Optional.of(entity));
        when(creditNoteEntityMapper.toDomain(entity)).thenReturn(domain);

        assertThat(adapter.findById(id)).isPresent().contains(domain);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findCreditNotes_usesSpecificationAndReturnsPageResult() {
        UUID tenantId = UUID.randomUUID();
        CreditNoteFilter filter = new CreditNoteFilter(tenantId, null, null, null, null, null);
        CreditNoteEntity entity = CreditNoteEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).build();
        CreditNote domain = openCreditNote(entity.getId(), tenantId);

        when(creditNoteJpaRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(entity)));
        when(creditNoteEntityMapper.toDomain(entity)).thenReturn(domain);

        PageResult<CreditNote> result = adapter.findCreditNotes(filter, 0, 20, "createdAt", "DESC");

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getTenantId()).isEqualTo(tenantId);
    }

    private CreditNote openCreditNote(UUID id, UUID tenantId) {
        return CreditNote.builder()
            .id(id).tenantId(tenantId).invoiceId(UUID.randomUUID())
            .creditNumber("CN-001").amountMinor(1000L).currency("INR")
            .reason("test").status(CreditNoteStatus.OPEN).createdAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }
}
