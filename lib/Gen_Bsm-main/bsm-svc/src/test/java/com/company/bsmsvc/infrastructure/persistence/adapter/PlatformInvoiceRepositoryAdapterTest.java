package com.company.bsmsvc.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.infrastructure.persistence.entity.PlatformInvoiceEntity;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.PlatformInvoiceEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.PlatformInvoiceJpaRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformInvoiceRepositoryAdapterTest {

    @Mock
    private PlatformInvoiceJpaRepository platformInvoiceJpaRepository;

    @Mock
    private PlatformInvoiceEntityMapper platformInvoiceEntityMapper;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PlatformInvoiceRepositoryAdapter platformInvoiceRepositoryAdapter;

    @Test
    void testSave() {
        UUID invoiceId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        PlatformInvoice domain = PlatformInvoice.builder().id(invoiceId).subscriptionId(subId).build();
        PlatformInvoiceEntity entity = PlatformInvoiceEntity.builder().id(invoiceId).build();
        SubscriptionEntity subEntity = SubscriptionEntity.builder().id(subId).build();

        when(platformInvoiceEntityMapper.toEntity(domain)).thenReturn(entity);
        when(entityManager.find(SubscriptionEntity.class, subId)).thenReturn(subEntity);
        when(platformInvoiceJpaRepository.save(entity)).thenReturn(entity);
        when(platformInvoiceEntityMapper.toDomain(entity)).thenReturn(domain);

        PlatformInvoice saved = platformInvoiceRepositoryAdapter.save(domain);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo(invoiceId);
        assertThat(entity.getSubscription()).isEqualTo(subEntity);
    }

    @Test
    void testFindById() {
        UUID id = UUID.randomUUID();
        PlatformInvoiceEntity entity = PlatformInvoiceEntity.builder().id(id).build();
        PlatformInvoice domain = PlatformInvoice.builder().id(id).build();

        when(platformInvoiceJpaRepository.findById(id)).thenReturn(Optional.of(entity));
        when(platformInvoiceEntityMapper.toDomain(entity)).thenReturn(domain);

        Optional<PlatformInvoice> result = platformInvoiceRepositoryAdapter.findById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
    }

    @Test
    void testFindByInvoiceNumber() {
        String num = "INV-100";
        PlatformInvoiceEntity entity = PlatformInvoiceEntity.builder().invoiceNumber(num).build();
        PlatformInvoice domain = PlatformInvoice.builder().invoiceNumber(num).build();

        when(platformInvoiceJpaRepository.findByInvoiceNumber(num)).thenReturn(Optional.of(entity));
        when(platformInvoiceEntityMapper.toDomain(entity)).thenReturn(domain);

        Optional<PlatformInvoice> result = platformInvoiceRepositoryAdapter.findByInvoiceNumber(num);

        assertThat(result).isPresent();
        assertThat(result.get().getInvoiceNumber()).isEqualTo(num);
    }

    @Test
    void testFindByTenantId() {
        UUID tenantId = UUID.randomUUID();
        PlatformInvoiceEntity entity = PlatformInvoiceEntity.builder().tenantId(tenantId).build();
        PlatformInvoice domain = PlatformInvoice.builder().tenantId(tenantId).build();

        when(platformInvoiceJpaRepository.findByTenantId(tenantId)).thenReturn(List.of(entity));
        when(platformInvoiceEntityMapper.toDomain(entity)).thenReturn(domain);

        List<PlatformInvoice> result = platformInvoiceRepositoryAdapter.findByTenantId(tenantId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void testFindBySubscriptionId() {
        UUID subId = UUID.randomUUID();
        PlatformInvoiceEntity entity = PlatformInvoiceEntity.builder().build();
        PlatformInvoice domain = PlatformInvoice.builder().subscriptionId(subId).build();

        when(platformInvoiceJpaRepository.findBySubscriptionId(subId)).thenReturn(List.of(entity));
        when(platformInvoiceEntityMapper.toDomain(entity)).thenReturn(domain);

        List<PlatformInvoice> result = platformInvoiceRepositoryAdapter.findBySubscriptionId(subId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSubscriptionId()).isEqualTo(subId);
    }

    @Test
    void testFindInvoices() {
        UUID tenantId = UUID.randomUUID();
        PlatformInvoiceEntity entity = PlatformInvoiceEntity.builder().tenantId(tenantId).build();
        PlatformInvoice domain = PlatformInvoice.builder().tenantId(tenantId).build();

        when(platformInvoiceJpaRepository.findAll(
            any(org.springframework.data.jpa.domain.Specification.class),
            any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new org.springframework.data.domain.PageImpl<PlatformInvoiceEntity>(List.of(entity)));
        when(platformInvoiceEntityMapper.toDomain(entity)).thenReturn(domain);

        com.company.bsmsvc.domain.model.PageResult<PlatformInvoice> result = platformInvoiceRepositoryAdapter.findInvoices(
            new com.company.bsmsvc.domain.model.InvoiceFilter(tenantId, null, null, null),
            0,
            20,
            "createdAt",
            "desc"
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getTenantId()).isEqualTo(tenantId);
    }
}
