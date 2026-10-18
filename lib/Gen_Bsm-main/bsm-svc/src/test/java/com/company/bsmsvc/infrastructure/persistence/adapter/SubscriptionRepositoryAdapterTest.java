package com.company.bsmsvc.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.infrastructure.persistence.entity.SubscriptionEntity;
import com.company.bsmsvc.infrastructure.persistence.mapper.SubscriptionEntityMapper;
import com.company.bsmsvc.infrastructure.persistence.repository.SubscriptionJpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionRepositoryAdapterTest {

    @Mock
    private SubscriptionJpaRepository subscriptionJpaRepository;

    @Mock
    private SubscriptionEntityMapper subscriptionEntityMapper;

    @InjectMocks
    private SubscriptionRepositoryAdapter subscriptionRepositoryAdapter;

    @Test
    void findCurrentByTenantIdShouldMapEntityToDomain() {
        UUID tenantId = UUID.randomUUID();
        SubscriptionEntity entity = SubscriptionEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY).build();
        Subscription domain = Subscription.builder().id(entity.getId()).tenantId(tenantId).status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY).build();

        when(subscriptionJpaRepository.findTopByTenantIdAndStatusInOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(tenantId), anyCollection())).thenReturn(Optional.of(entity));
        when(subscriptionEntityMapper.toDomain(entity)).thenReturn(domain);

        Optional<Subscription> result = subscriptionRepositoryAdapter.findCurrentByTenantId(tenantId);

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }
}
