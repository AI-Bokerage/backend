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
import com.company.bsmsvc.domain.event.ExternalSubscriptionCancelledEvent;
import com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionSynchronizationServiceTest {

    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private PaymentGatewayResolver resolver;
    @Mock private SubscriptionRepositoryPort subscriptionRepository;
    @Mock private SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    @Mock private PaymentMethodRepositoryPort paymentMethodRepository;
    @Mock private PaymentGatewayPort gateway;
    @InjectMocks private SubscriptionSynchronizationServiceImpl syncService;

    private UUID tenantId;
    private UUID subscriptionId;
    private UUID planVersionId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        planVersionId = UUID.randomUUID();
        when(resolver.resolve(any())).thenReturn(gateway);
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void syncCreate_skips_whenNoBillingProfile() {
        Subscription sub = subscription(subscriptionId, tenantId, null, planVersionId);
        when(billingProfileService.getProfile(tenantId))
            .thenThrow(new TenantBillingProfileNotFoundException("no profile"));

        Subscription result = syncService.syncCreate(sub);

        assertThat(result).isSameAs(sub);
        verify(gateway, never()).createSubscription(any());
    }

    @Test
    void syncCreate_skips_whenNoExternalCustomerId() {
        Subscription sub = subscription(subscriptionId, tenantId, null, planVersionId);
        TenantBillingProfile profile = profile(tenantId, null, PaymentProvider.STRIPE);
        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);

        Subscription result = syncCreate(sub);

        assertThat(result).isSameAs(sub);
        verify(gateway, never()).createSubscription(any());
    }

    @Test
    void syncCancel_cancelsProviderSubscription_whenExternalIdSet() {
        Subscription sub = subscription(subscriptionId, tenantId, "sub_existing", planVersionId);
        TenantBillingProfile profile = profile(tenantId, "cus_123", PaymentProvider.STRIPE);

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(subscriptionRepository.save(any())).thenReturn(sub);

        syncService.syncCancel(sub, true);

        verify(gateway).cancelSubscription(any());
        verify(subscriptionRepository).save(any());
    }

    @Test
    void syncCancel_skips_whenNoExternalSubscriptionId() {
        Subscription sub = subscription(subscriptionId, tenantId, null, planVersionId);

        syncService.syncCancel(sub, true);

        verify(gateway, never()).cancelSubscription(any());
    }

    @Test
    void syncCancel_registersExternalCancelledEvent() {
        Subscription sub = subscription(subscriptionId, tenantId, "sub_existing", planVersionId);
        TenantBillingProfile profile = profile(tenantId, "cus_123", PaymentProvider.STRIPE);

        when(billingProfileService.getProfile(tenantId)).thenReturn(profile);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> {
            Subscription saved = inv.getArgument(0);
            assertThat(saved.pullDomainEvents())
                .hasSize(1).first().isInstanceOf(ExternalSubscriptionCancelledEvent.class);
            return saved;
        });

        syncService.syncCancel(sub, true);
    }

    @Nested
    class PpmBacked {

        @Test
        void syncCreate_ppmBacked_skipsGateway() {
            UUID ppmPvId = UUID.randomUUID();
            Subscription sub = ppmSubscription(subscriptionId, tenantId, ppmPvId);
            when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));

            Subscription result = syncService.syncCreate(sub);

            assertThat(result).isSameAs(sub);
            verify(gateway, never()).createSubscription(any());
        }

        @Test
        void syncUpdate_ppmBacked_skipsGateway() {
            UUID ppmPvId = UUID.randomUUID();
            Subscription sub = ppmSubscription(subscriptionId, tenantId, ppmPvId);

            Subscription result = syncService.syncUpdate(sub);

            assertThat(result).isSameAs(sub);
            verify(gateway, never()).updateSubscription(any());
        }
    }

    private Subscription syncCreate(Subscription sub) {
        return syncService.syncCreate(sub);
    }

    private Subscription subscription(UUID id, UUID tenantId, String externalSubId, UUID planVersionId) {
        return Subscription.builder()
            .id(id).tenantId(tenantId)
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .planVersionId(planVersionId)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .externalSubscriptionId(externalSubId)
            .domainEvents(new ArrayList<>()).build();
    }

    private Subscription ppmSubscription(UUID id, UUID tenantId, UUID ppmPlanVersionId) {
        return Subscription.builder()
            .id(id).tenantId(tenantId)
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .ppmPlanVersionId(ppmPlanVersionId)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .domainEvents(new ArrayList<>()).build();
    }

    private TenantBillingProfile profile(UUID tenantId, String externalCustomerId, PaymentProvider provider) {
        return TenantBillingProfile.builder()
            .id(UUID.randomUUID()).tenantId(tenantId)
            .paymentProvider(provider).externalCustomerId(externalCustomerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .domainEvents(new ArrayList<>()).build();
    }
}
