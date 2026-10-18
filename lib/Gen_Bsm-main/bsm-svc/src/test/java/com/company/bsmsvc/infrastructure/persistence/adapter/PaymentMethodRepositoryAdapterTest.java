package com.company.bsmsvc.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.enums.PaymentMethodStatus;
import com.company.bsmsvc.domain.enums.PaymentMethodType;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.model.PaymentMethod;
import com.company.bsmsvc.infrastructure.persistence.entity.PaymentMethodEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.PaymentMethodEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.PaymentMethodJpaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentMethodRepositoryAdapterTest {

    @Mock private PaymentMethodJpaRepository jpaRepository;
    @Mock private PaymentMethodEntityMapper mapper;
    @InjectMocks private PaymentMethodRepositoryAdapter adapter;

    @Test
    void save_persistsAndReturnsDomain() {
        UUID id = UUID.randomUUID();
        PaymentMethod domain = pm(id);
        PaymentMethodEntity entity = entity(id);

        when(mapper.toEntity(domain)).thenReturn(entity);
        when(jpaRepository.save(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(domain);

        PaymentMethod result = adapter.save(domain);

        assertThat(result.getId()).isEqualTo(id);
        verify(jpaRepository).save(entity);
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());
        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void findById_returnsPresent_whenFound() {
        UUID id = UUID.randomUUID();
        PaymentMethodEntity entity = entity(id);
        PaymentMethod domain = pm(id);
        when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(adapter.findById(id)).isPresent().contains(domain);
    }

    @Test
    void findByTenantId_returnsMappedList() {
        UUID tenantId = UUID.randomUUID();
        UUID pmId = UUID.randomUUID();
        PaymentMethodEntity entity = entity(pmId);
        PaymentMethod domain = pm(pmId);
        when(jpaRepository.findByTenantId(tenantId)).thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(adapter.findByTenantId(tenantId)).hasSize(1);
    }

    @Test
    void existsByTenantIdAndExternalPaymentMethodId_delegates() {
        UUID tenantId = UUID.randomUUID();
        when(jpaRepository.existsByTenantIdAndExternalPaymentMethodId(tenantId, "pm_test")).thenReturn(true);
        assertThat(adapter.existsByTenantIdAndExternalPaymentMethodId(tenantId, "pm_test")).isTrue();
    }

    @Test
    void unsetDefaultForTenant_callsRepository() {
        UUID tenantId = UUID.randomUUID();
        adapter.unsetDefaultForTenant(tenantId);
        verify(jpaRepository).unsetDefaultForTenant(tenantId);
    }

    private PaymentMethod pm(UUID id) {
        return PaymentMethod.builder()
            .id(id).tenantId(UUID.randomUUID()).paymentProvider(PaymentProvider.STRIPE)
            .externalPaymentMethodId("pm_test").type(PaymentMethodType.CARD)
            .status(PaymentMethodStatus.ACTIVE).createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }

    private PaymentMethodEntity entity(UUID id) {
        return PaymentMethodEntity.builder()
            .id(id).tenantId(UUID.randomUUID()).paymentProvider(PaymentProvider.STRIPE)
            .externalPaymentMethodId("pm_test").type(PaymentMethodType.CARD)
            .status(PaymentMethodStatus.ACTIVE).createdAt(Instant.now()).updatedAt(Instant.now())
            .version(0L).build();
    }
}
