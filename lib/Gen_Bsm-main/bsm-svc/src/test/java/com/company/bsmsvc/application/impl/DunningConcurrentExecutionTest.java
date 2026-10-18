package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.domain.port.DunningEventPublisher;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.model.DunningPolicy;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.DunningAttemptStatus;
import com.company.bsmsvc.domain.enums.DunningStatus;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.model.DunningAttempt;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.DunningAttemptRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.company.bsmsvc.domain.exception.ConcurrentUpdateException;

/**
 * CRIT-1: Verifies that concurrent scheduler execution on the same dunning attempt
 * is handled gracefully — the optimistic lock exception is caught and the retry
 * loop continues for other attempts.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DunningConcurrentExecutionTest {

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
            dunningPolicy, null, null, null);
        when(subscriptionHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dunningAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Test 1: Optimistic lock on first attempt — second attempt still processed ──

    @Test
    void processDueAttempts_optimisticLockOnFirst_continuesForSecond() {
        DunningAttempt lockedAttempt = pendingAttempt(1, UUID.randomUUID());
        DunningAttempt otherAttempt = pendingAttempt(1, UUID.randomUUID());

        Subscription sub1 = dunningSubscription(DunningStatus.DUNNING_DAY_1, lockedAttempt.getSubscriptionId());
        Subscription sub2 = dunningSubscription(DunningStatus.DUNNING_DAY_1, otherAttempt.getSubscriptionId());

        // First attempt: save marks IN_PROGRESS then throws optimistic lock exception
        when(dunningAttemptRepository.findDuePending(any())).thenReturn(List.of(lockedAttempt, otherAttempt));
        when(subscriptionRepository.findById(lockedAttempt.getSubscriptionId())).thenReturn(Optional.of(sub1));
        when(subscriptionRepository.findById(otherAttempt.getSubscriptionId())).thenReturn(Optional.of(sub2));
        when(paymentMethodRepository.findDefaultByTenantId(any())).thenReturn(Optional.empty());

        // Simulate that the first save (marking IN_PROGRESS) throws optimistic lock on sub1
        when(dunningAttemptRepository.save(any(DunningAttempt.class)))
            .thenThrow(new ConcurrentUpdateException("concurrent update", null))
            .thenAnswer(inv -> inv.getArgument(0)); // subsequent saves succeed

        dunningService.processDueAttempts();

        // sub2 must have been processed (dunning advanced from no PM)
        assertThat(sub2.getDunningStatus()).isEqualTo(DunningStatus.DUNNING_DAY_3);
    }

    // ── Test 2: Optimistic lock exception does NOT propagate — loop completes ────

    @Test
    void processDueAttempts_optimisticLock_neverPropagatesException() {
        DunningAttempt attempt = pendingAttempt(1, UUID.randomUUID());
        Subscription sub = dunningSubscription(DunningStatus.DUNNING_DAY_1, attempt.getSubscriptionId());

        when(dunningAttemptRepository.findDuePending(any())).thenReturn(List.of(attempt));
        when(subscriptionRepository.findById(attempt.getSubscriptionId())).thenReturn(Optional.of(sub));
        when(dunningAttemptRepository.save(any()))
            .thenThrow(new ConcurrentUpdateException("concurrent update", null));

        // Must not throw — the optimistic lock exception must be swallowed
        dunningService.processDueAttempts();

        // Provider was never called (we never reached the gateway call)
        verify(resolver, never()).resolve(any());
    }

    // ── Test 3: Version field flows through mapper round-trip ───────────────────

    @Test
    void dunningAttempt_version_survivesBuilderRoundTrip() {
        DunningAttempt attempt = DunningAttempt.builder()
            .id(UUID.randomUUID()).version(3L).subscriptionId(subscriptionId)
            .tenantId(tenantId).invoiceId(invoiceId).attemptNumber(1)
            .status(DunningAttemptStatus.PENDING).nextRetryAt(Instant.now())
            .createdAt(Instant.now()).build();

        DunningAttempt updated = attempt.toBuilder()
            .status(DunningAttemptStatus.IN_PROGRESS).attemptedAt(Instant.now()).build();

        assertThat(updated.getVersion()).isEqualTo(3L);
        assertThat(updated.getStatus()).isEqualTo(DunningAttemptStatus.IN_PROGRESS);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private DunningAttempt pendingAttempt(int num, UUID subId) {
        return DunningAttempt.builder()
            .id(UUID.randomUUID()).version(0L).subscriptionId(subId).tenantId(tenantId)
            .invoiceId(invoiceId).attemptNumber(num).status(DunningAttemptStatus.PENDING)
            .nextRetryAt(Instant.now().minusSeconds(60)).createdAt(Instant.now()).build();
    }

    private Subscription dunningSubscription(DunningStatus ds, UUID subId) {
        return Subscription.builder()
            .id(subId).tenantId(tenantId).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.PAST_DUE).billingCycle(BillingCycle.MONTHLY)
            .dunningStatus(ds).dunningStartedAt(Instant.now().minusSeconds(3600))
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .domainEvents(new ArrayList<>()).build();
    }
}
