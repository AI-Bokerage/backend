package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrialExpiryServiceImplTest {

    @Mock private SubscriptionRepositoryPort subscriptionRepository;
    @Mock private SubscriptionHistoryRepositoryPort subscriptionHistoryRepository;
    @Mock private SubscriptionEventRepositoryPort subscriptionEventRepository;
    @Mock private SubscriptionEventPublisherPort subscriptionEventPublisher;
    @Mock private SubscriptionLifecycleMapper lifecycleMapper;
    @InjectMocks private TrialExpiryServiceImpl service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID SUB_ID    = UUID.randomUUID();

    private Subscription trialSub(Instant trialEndsAt) {
        return Subscription.builder()
            .id(SUB_ID).tenantId(TENANT_ID).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.TRIALING).billingCycle(BillingCycle.MONTHLY)
            .trialEndsAt(trialEndsAt)
            .currentPeriodStart(Instant.now().minusSeconds(14 * 86400L))
            .currentPeriodEnd(trialEndsAt)
            .domainEvents(new ArrayList<>())
            .createdAt(Instant.now().minusSeconds(14 * 86400L))
            .updatedAt(Instant.now())
            .build();
    }

    @Test
    void processExpiredTrials_noExpired_doesNothing() {
        when(subscriptionRepository.findExpiredTrials(any())).thenReturn(List.of());

        service.processExpiredTrials();

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void expireOne_trialing_transitionsToActive() {
        Subscription sub = trialSub(Instant.now().minusSeconds(3600));
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lifecycleMapper.toEvent(any(), any(), any(), any(), any(), any()))
            .thenReturn(com.company.bsmsvc.domain.model.SubscriptionEvent.builder()
                .id(UUID.randomUUID()).subscriptionId(SUB_ID).tenantId(TENANT_ID)
                .eventType(com.company.bsmsvc.domain.enums.SubscriptionEventType.SUBSCRIPTION_ACTIVATED)
                .occurredAt(Instant.now()).eventVersion(1).build());

        service.expireOne(sub);

        ArgumentCaptor<Subscription> cap = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        verify(subscriptionHistoryRepository).save(any());
        verify(subscriptionEventRepository).save(any());
        verify(subscriptionEventPublisher).publishChanged(any(), any(), any());
    }

    @Test
    void expireOne_alreadyActive_isSkipped() {
        Subscription active = Subscription.builder()
            .id(SUB_ID).tenantId(TENANT_ID).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .domainEvents(new ArrayList<>()).build();
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(active));

        service.expireOne(active);

        verify(subscriptionRepository, never()).save(any());
        verify(subscriptionEventPublisher, never()).publishChanged(any(), any(), any());
    }

    @Test
    void expireOne_subscriptionNotFound_isSkipped() {
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.empty());

        Subscription ghost = trialSub(Instant.now().minusSeconds(3600));
        service.expireOne(ghost);

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void processExpiredTrials_multipleExpired_processesAll() {
        UUID subId2 = UUID.randomUUID();
        Subscription sub1 = trialSub(Instant.now().minusSeconds(100));
        Subscription sub2 = Subscription.builder()
            .id(subId2).tenantId(UUID.randomUUID()).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.TRIALING).billingCycle(BillingCycle.MONTHLY)
            .trialEndsAt(Instant.now().minusSeconds(200))
            .currentPeriodStart(Instant.now().minusSeconds(14 * 86400L))
            .currentPeriodEnd(Instant.now().minusSeconds(200))
            .domainEvents(new ArrayList<>()).build();

        when(subscriptionRepository.findExpiredTrials(any())).thenReturn(List.of(sub1, sub2));
        when(subscriptionRepository.findById(SUB_ID)).thenReturn(Optional.of(sub1));
        when(subscriptionRepository.findById(subId2)).thenReturn(Optional.of(sub2));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(lifecycleMapper.toEvent(any(), any(), any(), any(), any(), any()))
            .thenReturn(com.company.bsmsvc.domain.model.SubscriptionEvent.builder()
                .id(UUID.randomUUID()).subscriptionId(SUB_ID).tenantId(TENANT_ID)
                .eventType(com.company.bsmsvc.domain.enums.SubscriptionEventType.SUBSCRIPTION_ACTIVATED)
                .occurredAt(Instant.now()).eventVersion(1).build());

        service.processExpiredTrials();

        verify(subscriptionRepository, org.mockito.Mockito.times(2)).save(any());
        verify(subscriptionEventPublisher, org.mockito.Mockito.times(2)).publishChanged(any(), any(), any());
    }
}
