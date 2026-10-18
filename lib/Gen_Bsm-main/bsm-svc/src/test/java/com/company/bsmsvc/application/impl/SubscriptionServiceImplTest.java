package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper;
import com.company.bsmsvc.application.service.SubscriptionSynchronizationService;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.event.SubscriptionDowngradeCancelledEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.TrialAlreadyConsumedException;
import com.company.bsmsvc.domain.port.TenantTrialRecordRepositoryPort;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionScheduleRepositoryPort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.company.bsmsvc.domain.port.TenantScopePort;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionServiceImplTest {

    @Mock
    private SubscriptionRepositoryPort subscriptionRepositoryPort;

    @Mock
    private SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;

    @Mock
    private SubscriptionEventRepositoryPort subscriptionEventRepositoryPort;

    @Mock
    private SubscriptionScheduleRepositoryPort subscriptionScheduleRepositoryPort;

    @Mock
    private TenantOwnershipValidator tenantOwnershipValidator;

    @Mock
    private SubscriptionSynchronizationService subscriptionSynchronizationService;

    @Spy
    private SubscriptionLifecycleMapper subscriptionLifecycleMapper = new SubscriptionLifecycleMapper();

    @Mock private TenantScopePort tenantScopeEnforcer;

    @Mock private com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort subscriptionEventPublisher;

    @Mock private TenantTrialRecordRepositoryPort tenantTrialRecordRepository;

    @Mock private com.company.bsmsvc.domain.port.UserUsagePort userUsagePort;

    @Mock private com.company.bsmsvc.domain.port.UsageLimitsSeedingPort usgLimitsSeedingService;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;


    @org.junit.jupiter.api.BeforeEach
    void setupEnforcer() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
    }


    @org.junit.jupiter.api.BeforeEach
    void setupTrialRecord() {
        lenient().when(tenantTrialRecordRepository.existsByTenantId(any())).thenReturn(false);
        lenient().doNothing().when(tenantTrialRecordRepository).markTrialConsumed(any(), any(), any());
        lenient().when(userUsagePort.getUserUsageCounts(any())).thenReturn(new com.company.bsmsvc.domain.model.UserUsageCounts(0, 0));
    }

    @org.junit.jupiter.api.BeforeEach
    void setupEventPublisher() {
        lenient().doNothing().when(subscriptionEventPublisher).publishCreated(any());
        lenient().doNothing().when(subscriptionEventPublisher).publishChanged(any(), any(), any());
        lenient().doNothing().when(subscriptionEventPublisher).publishCanceled(any());
        lenient().doNothing().when(subscriptionEventPublisher).publishExpired(any());
        lenient().doNothing().when(subscriptionEventPublisher).publishUpgraded(any(), any());
        lenient().doNothing().when(subscriptionEventPublisher).publishRenewed(any());
    }

    @Test
    void createSubscriptionShouldPersistWhenTenantHasNoCurrentSubscription() {
        UUID planVersionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription draft = Subscription.builder()
            .tenantId(tenantId)
            .planVersionId(planVersionId)
            .billingCycle(BillingCycle.MONTHLY)
            .build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId)).thenReturn(Optional.empty());
        when(subscriptionRepositoryPort.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionSynchronizationService.syncCreate(any())).thenAnswer(inv -> inv.getArgument(0));

        Subscription result = subscriptionService.createSubscription(draft, 0, "create", "tester");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCurrentPeriodEnd()).isAfter(result.getCurrentPeriodStart());
    }

    @Test
    void createSubscriptionShouldFailWhenTenantAlreadyHasActiveSubscription() {
        UUID planVersionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription draft = Subscription.builder()
            .tenantId(tenantId)
            .planVersionId(planVersionId)
            .billingCycle(BillingCycle.MONTHLY)
            .build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId)).thenReturn(Optional.of(Subscription.builder().build()));

        assertThatThrownBy(() -> subscriptionService.createSubscription(draft, 0, "create", "tester"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("already has an active subscription");
    }

    @Test
    void cancelSubscriptionShouldSetCancelledStatusWhenImmediate() {
        UUID tenantId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Subscription current = Subscription.builder()
            .id(subscriptionId)
            .tenantId(tenantId)
            .planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId)).thenReturn(Optional.of(current));
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(current));
        when(subscriptionRepositoryPort.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doNothing().when(subscriptionSynchronizationService).syncCancel(any(), any(boolean.class));

        Subscription result = subscriptionService.cancelSubscription(tenantId, "requested", "tester", true);

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(result.getCancelledAt()).isNotNull();
    }

    @Test
    void resumeSubscriptionShouldNotTriggerExternalSyncUpdate() {
        UUID tenantId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Subscription paused = Subscription.builder()
            .id(subscriptionId)
            .tenantId(tenantId)
            .planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.PAUSED)
            .billingCycle(BillingCycle.MONTHLY)
            .externalSubscriptionId("sub_test_123")
            .build();

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(paused));
        when(subscriptionRepositoryPort.save(any(Subscription.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Subscription result = subscriptionService.resumeSubscription(subscriptionId, tenantId, "resume", "tester");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(subscriptionSynchronizationService, never()).syncUpdate(any());
    }

    // ── Fix 1: cancelDowngrade domain event registration ─────────────────────

    @Test
    void cancelDowngrade_registersDowngradeCancelledEventOnAggregate() {
        UUID tenantId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Subscription sub = Subscription.builder()
            .id(subscriptionId).tenantId(tenantId).planVersionId(planId)
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400))
            .build();
        SubscriptionSchedule pendingSchedule = SubscriptionSchedule.builder()
            .id(UUID.randomUUID()).subscriptionId(subscriptionId).tenantId(tenantId)
            .actionType(SubscriptionScheduleActionType.DOWNGRADE_SUBSCRIPTION)
            .targetPlanVersionId(UUID.randomUUID()).effectiveAt(Instant.now().plusSeconds(86400))
            .status(SubscriptionScheduleStatus.PENDING).createdBy(createdBy()).build();

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(sub));
        when(subscriptionScheduleRepositoryPort.findPendingBySubscriptionIdAndActionType(subscriptionId, SubscriptionScheduleActionType.DOWNGRADE_SUBSCRIPTION))
            .thenReturn(Optional.of(pendingSchedule));
        when(subscriptionScheduleRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));

        subscriptionService.cancelDowngrade(subscriptionId, tenantId, "changed mind", "user");

        assertThat(sub.pullDomainEvents())
            .hasSize(1).first().isInstanceOf(SubscriptionDowngradeCancelledEvent.class);
    }

    // ── Fix 2: Tenant validation on create ────────────────────────────────────

    @Test
    void createSubscription_throwsWhenTenantIdIsNull() {
        Subscription draft = Subscription.builder()
            .tenantId(null)
            .planVersionId(UUID.randomUUID())
            .billingCycle(BillingCycle.MONTHLY)
            .build();

        assertThatThrownBy(() -> subscriptionService.createSubscription(draft, 0, "reason", "actor"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("tenantId must be provided");
    }

    @Test
    void createSubscription_succeedsWithValidTenantId() {
        UUID tenantId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        Subscription draft = Subscription.builder()
            .tenantId(tenantId).planVersionId(planVersionId).billingCycle(BillingCycle.MONTHLY).build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId)).thenReturn(Optional.empty());
        when(subscriptionRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionSynchronizationService.syncCreate(any())).thenAnswer(i -> i.getArgument(0));

        Subscription result = subscriptionService.createSubscription(draft, 0, "r", "a");
        assertThat(result.getTenantId()).isEqualTo(tenantId);
    }

    // ── Fix 3: Pause/Resume is LOCAL ONLY — provider subscription unaffected ──

    @Test
    void pauseSubscription_doesNotCallProviderSync() {
        UUID tenantId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Subscription active = Subscription.builder()
            .id(subscriptionId).tenantId(tenantId).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .externalSubscriptionId("sub_stripe_123")
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400)).build();

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(active));
        when(subscriptionRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));

        Subscription result = subscriptionService.pauseSubscription(subscriptionId, tenantId, "billing pause", "actor");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
        verify(subscriptionSynchronizationService, never()).syncUpdate(any());
        verify(subscriptionSynchronizationService, never()).syncCancel(any(), any(boolean.class));
    }

    @Test
    void resumeSubscription_doesNotCallProviderSync_andPreservesExternalId() {
        UUID tenantId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Subscription paused = Subscription.builder()
            .id(subscriptionId).tenantId(tenantId).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.PAUSED).billingCycle(BillingCycle.MONTHLY)
            .externalSubscriptionId("sub_stripe_unchanged")
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400)).build();

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(paused));
        when(subscriptionRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));

        Subscription result = subscriptionService.resumeSubscription(subscriptionId, tenantId, "resume", "actor");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getExternalSubscriptionId()).isEqualTo("sub_stripe_unchanged");
        verify(subscriptionSynchronizationService, never()).syncUpdate(any());
        verify(subscriptionSynchronizationService, never()).syncCreate(any());
    }

    @Test
    void pauseAndResume_preservesExternalSubscriptionId() {
        UUID tenantId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        String externalId = "sub_test_immutable";
        Subscription active = Subscription.builder()
            .id(subscriptionId).tenantId(tenantId).planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE).billingCycle(BillingCycle.MONTHLY)
            .externalSubscriptionId(externalId)
            .currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now().plusSeconds(86400)).build();

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(active));
        when(subscriptionRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));

        Subscription paused = subscriptionService.pauseSubscription(subscriptionId, tenantId, "r", "a");
        assertThat(paused.getExternalSubscriptionId()).isEqualTo(externalId);

        Subscription pausedForResume = paused.toBuilder().status(SubscriptionStatus.PAUSED).build();
        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(pausedForResume));

        Subscription resumed = subscriptionService.resumeSubscription(subscriptionId, tenantId, "r", "a");
        assertThat(resumed.getExternalSubscriptionId()).isEqualTo(externalId);
    }

    // ── CRIT-4: syncCreate is invoked ─────────────────────────────────────────

    @Test
    void createSubscription_syncCreateIsInvoked() {
        UUID tenantId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        Subscription draft = Subscription.builder()
            .tenantId(tenantId).planVersionId(planVersionId).billingCycle(BillingCycle.MONTHLY).build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId)).thenReturn(Optional.empty());
        when(subscriptionRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionSynchronizationService.syncCreate(any())).thenAnswer(i -> i.getArgument(0));

        subscriptionService.createSubscription(draft, 0, "test", "actor");

        verify(subscriptionSynchronizationService).syncCreate(any(Subscription.class));
    }

    @Test
    void createSubscription_syncCreateFailure_doesNotPreventBsmSave() {
        UUID tenantId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        Subscription draft = Subscription.builder()
            .tenantId(tenantId).planVersionId(planVersionId).billingCycle(BillingCycle.MONTHLY).build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId)).thenReturn(Optional.empty());
        when(subscriptionRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionSynchronizationService.syncCreate(any()))
            .thenThrow(new com.company.bsmsvc.domain.exception.PaymentGatewayException("Stripe down"));

        try {
            subscriptionService.createSubscription(draft, 0, "test", "actor");
        } catch (Exception ignored) {
            // expected in unit-test else-branch
        }
        verify(subscriptionRepositoryPort).save(any(Subscription.class));
    }

    // ── One-trial-per-lifetime enforcement ───────────────────────────────────

    @Test
    void createSubscription_firstTrialAllowed_andTrialRecordWritten() {
        UUID tenantId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        Subscription draft = Subscription.builder()
            .tenantId(tenantId).planVersionId(planVersionId).billingCycle(BillingCycle.MONTHLY).build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId)).thenReturn(Optional.empty());
        when(tenantTrialRecordRepository.existsByTenantId(tenantId)).thenReturn(false);
        when(subscriptionRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionSynchronizationService.syncCreate(any())).thenAnswer(i -> i.getArgument(0));

        Subscription result = subscriptionService.createSubscription(draft, 14, "onboarding", "SYSTEM");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(result.getTrialEndsAt()).isNotNull();
        verify(tenantTrialRecordRepository).markTrialConsumed(
            org.mockito.ArgumentMatchers.eq(tenantId), any(UUID.class), any(java.time.Instant.class));
    }

    @Test
    void createSubscription_secondTrialRejected_whenTrialRecordExists() {
        UUID tenantId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        Subscription draft = Subscription.builder()
            .tenantId(tenantId).planVersionId(planVersionId).billingCycle(BillingCycle.MONTHLY).build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId)).thenReturn(Optional.empty());
        when(tenantTrialRecordRepository.existsByTenantId(tenantId)).thenReturn(true);

        assertThatThrownBy(() -> subscriptionService.createSubscription(draft, 14, "attempt", "user"))
            .isInstanceOf(TrialAlreadyConsumedException.class)
            .hasMessageContaining("already consumed");

        verify(subscriptionRepositoryPort, never()).save(any());
        verify(tenantTrialRecordRepository, never()).markTrialConsumed(any(), any(), any());
    }

    @Test
    void createSubscription_afterCancellation_trialRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        Subscription draft = Subscription.builder()
            .tenantId(tenantId).planVersionId(planVersionId).billingCycle(BillingCycle.MONTHLY).build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId)).thenReturn(Optional.empty());
        when(tenantTrialRecordRepository.existsByTenantId(tenantId)).thenReturn(true);

        assertThatThrownBy(() -> subscriptionService.createSubscription(draft, 14, "re-trial attempt", "admin"))
            .isInstanceOf(TrialAlreadyConsumedException.class);
    }

    @Test
    void createSubscription_withoutTrialDays_allowedEvenWhenTrialRecordExists() {
        UUID tenantId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        Subscription draft = Subscription.builder()
            .tenantId(tenantId).planVersionId(planVersionId).billingCycle(BillingCycle.MONTHLY).build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId)).thenReturn(Optional.empty());
        when(tenantTrialRecordRepository.existsByTenantId(tenantId)).thenReturn(true);
        when(subscriptionRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionSynchronizationService.syncCreate(any())).thenAnswer(i -> i.getArgument(0));

        Subscription result = subscriptionService.createSubscription(draft, 0, "paid re-subscribe", "admin");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.getTrialEndsAt()).isNull();
        verify(tenantTrialRecordRepository, never()).markTrialConsumed(any(), any(), any());
    }

    @Test
    void createSubscription_withoutTrialDays_doesNotWriteTrialRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        Subscription draft = Subscription.builder()
            .tenantId(tenantId).planVersionId(planVersionId).billingCycle(BillingCycle.MONTHLY).build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId)).thenReturn(Optional.empty());
        when(tenantTrialRecordRepository.existsByTenantId(tenantId)).thenReturn(false);
        when(subscriptionRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionHistoryRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionEventRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        when(subscriptionSynchronizationService.syncCreate(any())).thenAnswer(i -> i.getArgument(0));

        Subscription result = subscriptionService.createSubscription(draft, 0, "direct", "admin");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(tenantTrialRecordRepository, never()).markTrialConsumed(any(), any(), any());
    }

    @Test
    void createSubscription_existingActiveSubscription_isUnaffectedByTrialCheck() {
        UUID tenantId = UUID.randomUUID();
        UUID planVersionId = UUID.randomUUID();
        Subscription draft = Subscription.builder()
            .tenantId(tenantId).planVersionId(planVersionId).billingCycle(BillingCycle.MONTHLY).build();

        when(subscriptionRepositoryPort.findCurrentByTenantId(tenantId))
            .thenReturn(Optional.of(Subscription.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).status(SubscriptionStatus.ACTIVE).build()));
        when(tenantTrialRecordRepository.existsByTenantId(tenantId)).thenReturn(true);

        assertThatThrownBy(() -> subscriptionService.createSubscription(draft, 14, "dup", "admin"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("already has an active subscription")
            .isNotInstanceOf(TrialAlreadyConsumedException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID createdBy() { return UUID.randomUUID(); }
}
