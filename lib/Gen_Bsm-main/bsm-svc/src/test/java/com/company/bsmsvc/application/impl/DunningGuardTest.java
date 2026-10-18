package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.model.DunningPolicy;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.DunningAttemptStatus;
import com.company.bsmsvc.domain.enums.DunningStatus;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.DunningAttempt;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.DunningAttemptRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.DunningEventPublisher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DunningGuardTest {

    @Mock private DunningAttemptRepositoryPort dunningAttemptRepository;
    @Mock private SubscriptionRepositoryPort subscriptionRepository;
    @Mock private SubscriptionHistoryRepositoryPort subscriptionHistoryRepository;
    @Mock private PaymentMethodRepositoryPort paymentMethodRepository;
    @Mock private PaymentRepositoryPort paymentRepository;
    @Mock private BillingLedgerRepositoryPort ledgerRepository;
    @Mock private TenantBillingProfileService billingProfileService;
    @Mock private PaymentGatewayResolver resolver;
    @Mock private InvoiceService invoiceService;
    private final DunningPolicy dunningPolicy = new DunningPolicy(24, 72, 168, 14, 30);
    @Mock private DunningEventPublisher dunningEventPublisher;
    @Mock private com.company.bsmsvc.domain.port.EventPublisherPort auditEventPublisher;
    private DunningServiceImpl dunningService;

    private UUID tenantId;
    private UUID subscriptionId;
    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        dunningService = new DunningServiceImpl(
            dunningEventPublisher, dunningAttemptRepository, subscriptionRepository,
            subscriptionHistoryRepository, paymentMethodRepository, paymentRepository,
            ledgerRepository, billingProfileService, resolver, invoiceService,
            dunningPolicy, null, null, auditEventPublisher);
        when(dunningAttemptRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void startDunning_skipsWhenSubscriptionIsPaused() {
        Subscription sub = subscription(SubscriptionStatus.PAUSED, DunningStatus.NORMAL);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));

        dunningService.startDunning(subscriptionId, invoiceId);

        verify(dunningAttemptRepository, never()).save(any(DunningAttempt.class));
        assertThat(sub.getDunningStatus()).isEqualTo(DunningStatus.NORMAL);
    }

    @Test
    void startDunning_skipsWhenSubscriptionIsCancelled() {
        Subscription sub = subscription(SubscriptionStatus.CANCELLED, DunningStatus.NORMAL);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));

        dunningService.startDunning(subscriptionId, invoiceId);

        verify(dunningAttemptRepository, never()).save(any(DunningAttempt.class));
    }

    @Test
    void startDunning_skipsWhenSubscriptionIsSuspended() {
        Subscription sub = subscription(SubscriptionStatus.SUSPENDED_PENDING_PURGE, DunningStatus.SUSPENDED_PENDING_PURGE);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));

        dunningService.startDunning(subscriptionId, invoiceId);

        verify(dunningAttemptRepository, never()).save(any(DunningAttempt.class));
    }

    @Test
    void startDunning_isIdempotent_alreadyInDunning() {
        Subscription sub = subscription(SubscriptionStatus.PAST_DUE, DunningStatus.DUNNING_DAY_1);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));

        dunningService.startDunning(subscriptionId, invoiceId);

        verify(dunningAttemptRepository, never()).save(any(DunningAttempt.class));
    }

    @Test
    void startDunning_succeedsForActiveSubscription() {
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, DunningStatus.NORMAL);
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));
        when(subscriptionHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        dunningService.startDunning(subscriptionId, invoiceId);

        verify(dunningAttemptRepository).save(any(DunningAttempt.class));
        assertThat(sub.getDunningStatus()).isEqualTo(DunningStatus.DUNNING_DAY_1);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
    }

    @Test
    void processDueAttempts_skipsIfSubscriptionNotInDunning() {
        Subscription sub = subscription(SubscriptionStatus.ACTIVE, DunningStatus.NORMAL);
        DunningAttempt attempt = DunningAttempt.builder()
            .id(UUID.randomUUID()).subscriptionId(subscriptionId).tenantId(tenantId).invoiceId(invoiceId)
            .attemptNumber(1).status(DunningAttemptStatus.PENDING)
            .nextRetryAt(Instant.now().minusSeconds(60)).createdAt(Instant.now()).build();

        when(dunningAttemptRepository.findDuePending(any())).thenReturn(java.util.List.of(attempt));
        when(subscriptionRepository.findById(subscriptionId)).thenReturn(Optional.of(sub));

        dunningService.processDueAttempts();

        // Should skip because isInDunning() returns false
        verify(subscriptionRepository, never()).save(any());
    }

    private Subscription subscription(SubscriptionStatus status, DunningStatus dunningStatus) {
        return Subscription.builder()
            .id(subscriptionId).tenantId(tenantId).planVersionId(UUID.randomUUID())
            .status(status).billingCycle(BillingCycle.MONTHLY)
            .dunningStatus(dunningStatus)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .domainEvents(new ArrayList<>()).build();
    }
}
