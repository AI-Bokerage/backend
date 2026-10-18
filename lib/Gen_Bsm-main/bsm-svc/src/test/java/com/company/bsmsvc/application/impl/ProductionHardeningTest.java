package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper;
import com.company.bsmsvc.application.util.PaginationUtils;
import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionEvent;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionScheduleRepositoryPort;
import com.company.bsmsvc.domain.service.TenantOwnershipValidator;
import java.time.Instant;
import java.util.List;
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

/**
 * Production-hardening tests covering: tenant ownership, idempotency, schedule conflicts,
 * pagination clamping, event versioning, actor model, domain event aggregation, and plan immutability.
 */
@ExtendWith(MockitoExtension.class)
class ProductionHardeningTest {

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

    @Spy
    private SubscriptionLifecycleMapper subscriptionLifecycleMapper = new SubscriptionLifecycleMapper();

    @Mock private TenantScopePort tenantScopeEnforcer;
    @Mock private com.company.bsmsvc.domain.port.UserUsagePort userUsagePort;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    // ─────────────────────────────────────────────────────────────────────────
    // FIX 1: Tenant Ownership Validation
    // ─────────────────────────────────────────────────────────────────────────


    @org.junit.jupiter.api.BeforeEach
    void setupEnforcer() {
        lenient().when(tenantScopeEnforcer.resolveEffectiveTenantId(any())).thenAnswer(inv -> inv.getArgument(0));
        // Default: within limits — tests that care about usage blocking override these
        lenient().when(userUsagePort.getUserUsageCounts(any())).thenReturn(new com.company.bsmsvc.domain.model.UserUsageCounts(0, 0));
    }

    @Test
    void pauseWithWrongTenantIdShouldThrowBusinessRuleViolation() {
        UUID subscriptionId = UUID.randomUUID();
        UUID wrongTenantId = UUID.randomUUID();
        Subscription subscription = activeSubscription(subscriptionId, UUID.randomUUID());

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(subscription));
        doThrow(new BusinessRuleViolationException("Tenant does not own this subscription"))
            .when(tenantOwnershipValidator).validate(any(), any());

        assertThatThrownBy(() -> subscriptionService.pauseSubscription(subscriptionId, wrongTenantId, "test", "actor"))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Tenant does not own this subscription");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIX 2: Idempotency — pausing an already-paused subscription
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void pauseAlreadyPausedSubscriptionShouldReturnExistingSubscription() {
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription paused = Subscription.builder()
            .id(subscriptionId)
            .tenantId(tenantId)
            .planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.PAUSED)
            .billingCycle(BillingCycle.MONTHLY)
            .build();

        when(subscriptionRepositoryPort.findById(subscriptionId)).thenReturn(Optional.of(paused));

        Subscription result = subscriptionService.pauseSubscription(subscriptionId, tenantId, "re-pause", "actor");

        assertThat(result.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
        assertThat(result.getId()).isEqualTo(subscriptionId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIX 5: Pagination clamping
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void paginationClampNegativePageShouldReturnZero() {
        assertThat(PaginationUtils.clampPage(-5)).isEqualTo(0);
        assertThat(PaginationUtils.clampPage(0)).isEqualTo(0);
        assertThat(PaginationUtils.clampPage(3)).isEqualTo(3);
    }

    @Test
    void paginationClampSizeAboveMaxShouldReturnMax() {
        assertThat(PaginationUtils.clampSize(200)).isEqualTo(100);
        assertThat(PaginationUtils.clampSize(100)).isEqualTo(100);
    }

    @Test
    void paginationClampSizeBelowOneShouldReturnDefault() {
        assertThat(PaginationUtils.clampSize(0)).isEqualTo(PaginationUtils.DEFAULT_PAGE_SIZE);
        assertThat(PaginationUtils.clampSize(-1)).isEqualTo(PaginationUtils.DEFAULT_PAGE_SIZE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIX 6: Event versioning — new events have eventVersion = 1
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void newSubscriptionEventShouldHaveEventVersionOne() {
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Subscription subscription = activeSubscription(subscriptionId, tenantId);

        SubscriptionEvent event = subscriptionLifecycleMapper.toEvent(
            subscription,
            com.company.bsmsvc.domain.enums.SubscriptionEventType.SUBSCRIPTION_PAUSED,
            java.util.Map.of("reason", "test"),
            UUID.randomUUID(),
            ActorType.USER,
            Instant.now()
        );

        assertThat(event.getEventVersion()).isEqualTo(1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIX 7: Actor model — history and events carry actorType
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void subscriptionHistoryShouldCarryActorType() {
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Subscription subscription = activeSubscription(subscriptionId, tenantId);

        SubscriptionHistory history = subscriptionLifecycleMapper.toHistory(
            subscription,
            com.company.bsmsvc.domain.enums.SubscriptionHistoryAction.SUBSCRIPTION_PAUSED,
            subscription.getPlanVersionId(),
            subscription.getPlanVersionId(),
            "reason",
            "actor",
            actorId,
            ActorType.ADMIN,
            Instant.now()
        );

        assertThat(history.getActorType()).isEqualTo(ActorType.ADMIN);
        assertThat(history.getActorId()).isEqualTo(actorId);
    }

    @Test
    void subscriptionEventShouldCarryActorType() {
        UUID subscriptionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Subscription subscription = activeSubscription(subscriptionId, tenantId);

        SubscriptionEvent event = subscriptionLifecycleMapper.toEvent(
            subscription,
            com.company.bsmsvc.domain.enums.SubscriptionEventType.SUBSCRIPTION_RESUMED,
            java.util.Map.of(),
            actorId,
            ActorType.SYSTEM,
            Instant.now()
        );

        assertThat(event.getActorType()).isEqualTo(ActorType.SYSTEM);
        assertThat(event.getActorId()).isEqualTo(actorId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIX 8: Plan immutability — isExpired() returns true when period is in past
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void isExpiredShouldReturnTrueWhenCurrentPeriodEndIsInPast() {
        Subscription subscription = Subscription.builder()
            .id(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .currentPeriodEnd(Instant.now().minusSeconds(3600))
            .build();

        assertThat(subscription.isExpired()).isTrue();
    }

    @Test
    void isExpiredShouldReturnFalseWhenCurrentPeriodEndIsInFuture() {
        Subscription subscription = Subscription.builder()
            .id(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .currentPeriodEnd(Instant.now().plusSeconds(86400))
            .build();

        assertThat(subscription.isExpired()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FIX 9 & 10: Domain event aggregation — registerEvent + pullDomainEvents
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void domainEventAggregationShouldRegisterAndReturnEvents() {
        Subscription subscription = Subscription.builder()
            .id(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .build();

        Object event1 = new Object();
        Object event2 = new Object();
        subscription.registerEvent(event1);
        subscription.registerEvent(event2);

        List<Object> events = subscription.pullDomainEvents();
        assertThat(events).containsExactly(event1, event2);
    }

    @Test
    void clearDomainEventsShouldEmptyTheList() {
        Subscription subscription = Subscription.builder()
            .id(UUID.randomUUID())
            .tenantId(UUID.randomUUID())
            .planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .build();

        subscription.registerEvent(new Object());
        subscription.clearDomainEvents();

        assertThat(subscription.pullDomainEvents()).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Subscription activeSubscription(UUID id, UUID tenantId) {
        return Subscription.builder()
            .id(id)
            .tenantId(tenantId)
            .planVersionId(UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE)
            .billingCycle(BillingCycle.MONTHLY)
            .currentPeriodStart(Instant.now().minusSeconds(86400))
            .currentPeriodEnd(Instant.now().plusSeconds(86400 * 29))
            .cancelAtPeriodEnd(false)
            .build();
    }
}
