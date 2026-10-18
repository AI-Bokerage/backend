package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
class CancelSyncTest {

    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private PaymentGatewayResolver resolver;
    @Mock private SubscriptionRepositoryPort subscriptionRepository;

    @Mock private SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    @Mock private PaymentMethodRepositoryPort paymentMethodRepository;
    @Mock private PaymentGatewayPort gateway;
    @InjectMocks private SubscriptionSynchronizationServiceImpl syncService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        when(resolver.resolve(any())).thenReturn(gateway);
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void syncCancel_immediateCancel_callsGatewayWithImmediateTrue() {
        Subscription sub = sub("sub_ext_123");
        TenantBillingProfile profile = profile("cus_123");
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(subscriptionRepository.save(any())).thenReturn(sub);

        syncService.syncCancel(sub, true);

        ArgumentCaptor<com.company.bsmsvc.domain.model.payment.CancelSubscriptionCommand> captor =
            ArgumentCaptor.forClass(com.company.bsmsvc.domain.model.payment.CancelSubscriptionCommand.class);
        verify(gateway).cancelSubscription(captor.capture());
        assertThat(captor.getValue().immediately()).isTrue();
        assertThat(captor.getValue().externalSubscriptionId()).isEqualTo("sub_ext_123");
    }

    @Test
    void syncCancel_periodEndCancel_callsGatewayWithImmediateFalse() {
        Subscription sub = sub("sub_ext_123");
        TenantBillingProfile profile = profile("cus_123");
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(subscriptionRepository.save(any())).thenReturn(sub);

        syncService.syncCancel(sub, false);

        ArgumentCaptor<com.company.bsmsvc.domain.model.payment.CancelSubscriptionCommand> captor =
            ArgumentCaptor.forClass(com.company.bsmsvc.domain.model.payment.CancelSubscriptionCommand.class);
        verify(gateway).cancelSubscription(captor.capture());
        assertThat(captor.getValue().immediately()).isFalse();
    }

    @Test
    void syncCancel_noExternalSubscriptionId_skipsGatewayCall() {
        Subscription sub = sub(null);

        syncService.syncCancel(sub, true);

        verify(gateway, never()).cancelSubscription(any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void syncCancel_savesSubscriptionAfterProviderCancel() {
        Subscription sub = sub("sub_ext_123");
        TenantBillingProfile profile = profile("cus_123");
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(subscriptionRepository.save(any())).thenReturn(sub);

        syncService.syncCancel(sub, true);

        verify(subscriptionRepository).save(sub);
    }

    private Subscription sub(String externalSubId) {
        return Subscription.builder()
            .id(UUID.randomUUID()).tenantId(tenantId)
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .planVersionId(UUID.randomUUID())
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .externalSubscriptionId(externalSubId)
            .domainEvents(new ArrayList<>()).build();
    }

    private TenantBillingProfile profile(String customerId) {
        return TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId)
            .paymentProvider(PaymentProvider.STRIPE).externalCustomerId(customerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }
}
