package com.company.bsmsvc.application.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.CommercialEngineService;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleActionType;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import com.company.bsmsvc.domain.port.SubscriptionScheduleRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionScheduleExecutorServiceImplTest {

    @Mock private SubscriptionScheduleRepositoryPort scheduleRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private CommercialEngineService commercialEngineService;
    @InjectMocks private SubscriptionScheduleExecutorServiceImpl executor;

    private static final UUID SUB_ID       = UUID.randomUUID();
    private static final UUID TENANT_ID    = UUID.randomUUID();
    private static final UUID PLAN_VER_ID  = UUID.randomUUID();
    private static final UUID SCHEDULE_ID  = UUID.randomUUID();

    private SubscriptionSchedule schedule(SubscriptionScheduleActionType type) {
        return SubscriptionSchedule.builder()
            .id(SCHEDULE_ID).subscriptionId(SUB_ID).tenantId(TENANT_ID)
            .actionType(type)
            .targetPlanVersionId(type == SubscriptionScheduleActionType.DOWNGRADE_SUBSCRIPTION ? PLAN_VER_ID : null)
            .effectiveAt(Instant.now().minusSeconds(10))
            .status(SubscriptionScheduleStatus.PENDING)
            .createdBy(UUID.randomUUID())
            .createdAt(Instant.now().minusSeconds(3600))
            .updatedAt(Instant.now())
            .build();
    }

    @BeforeEach
    void setUp() {
        when(scheduleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── executeDueSchedules ───────────────────────────────────────────────────

    @Test
    void executeDueSchedules_noSchedules_doesNothing() {
        when(scheduleRepository.findDueSchedules(any())).thenReturn(List.of());

        executor.executeDueSchedules();

        verify(scheduleRepository, never()).save(any());
    }

    // ── CANCEL_SUBSCRIPTION ───────────────────────────────────────────────────

    @Test
    void executeOne_cancelSubscription_callsCancelAndMarksExecuted() {
        SubscriptionSchedule s = schedule(SubscriptionScheduleActionType.CANCEL_SUBSCRIPTION);
        when(scheduleRepository.findPendingBySubscriptionIdAndActionType(SUB_ID, SubscriptionScheduleActionType.CANCEL_SUBSCRIPTION))
            .thenReturn(Optional.of(s));
        when(subscriptionService.cancelSubscriptionById(any(), any(), any(), any(), anyBoolean()))
            .thenReturn(null);

        executor.executeOne(s);

        verify(subscriptionService).cancelSubscriptionById(
            eq(SUB_ID), eq(TENANT_ID), any(), eq("SCHEDULE_EXECUTOR"), eq(false));  // false = immediate

        ArgumentCaptor<SubscriptionSchedule> cap = ArgumentCaptor.forClass(SubscriptionSchedule.class);
        verify(scheduleRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(SubscriptionScheduleStatus.EXECUTED);
        assertThat(cap.getValue().getExecutedBy()).isEqualTo("SCHEDULE_EXECUTOR");
    }

    @Test
    void executeOne_cancelSubscription_serviceThrows_marksScheduleFailed() {
        SubscriptionSchedule s = schedule(SubscriptionScheduleActionType.CANCEL_SUBSCRIPTION);
        when(scheduleRepository.findPendingBySubscriptionIdAndActionType(SUB_ID, SubscriptionScheduleActionType.CANCEL_SUBSCRIPTION))
            .thenReturn(Optional.of(s));
        when(subscriptionService.cancelSubscriptionById(any(), any(), any(), any(), anyBoolean()))
            .thenThrow(new RuntimeException("payment gateway error"));

        executor.executeOne(s);

        ArgumentCaptor<SubscriptionSchedule> cap = ArgumentCaptor.forClass(SubscriptionSchedule.class);
        verify(scheduleRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(SubscriptionScheduleStatus.FAILED);
        assertThat(cap.getValue().getFailureReason()).contains("payment gateway error");
    }

    // ── DOWNGRADE_SUBSCRIPTION ────────────────────────────────────────────────

    @Test
    void executeOne_downgradeSubscription_callsPlanChangeAndMarksExecuted() {
        SubscriptionSchedule s = schedule(SubscriptionScheduleActionType.DOWNGRADE_SUBSCRIPTION);
        when(scheduleRepository.findPendingBySubscriptionIdAndActionType(SUB_ID, SubscriptionScheduleActionType.DOWNGRADE_SUBSCRIPTION))
            .thenReturn(Optional.of(s));
        when(commercialEngineService.applyScheduledPlanChange(any(), any(), any(), any()))
            .thenReturn(null);

        executor.executeOne(s);

        verify(commercialEngineService).applyScheduledPlanChange(
            eq(SUB_ID), eq(TENANT_ID), eq(PLAN_VER_ID), any());

        ArgumentCaptor<SubscriptionSchedule> cap = ArgumentCaptor.forClass(SubscriptionSchedule.class);
        verify(scheduleRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(SubscriptionScheduleStatus.EXECUTED);
    }

    @Test
    void executeOne_downgradeSubscription_noTargetPlanVersionId_marksScheduleFailed() {
        SubscriptionSchedule s = SubscriptionSchedule.builder()
            .id(SCHEDULE_ID).subscriptionId(SUB_ID).tenantId(TENANT_ID)
            .actionType(SubscriptionScheduleActionType.DOWNGRADE_SUBSCRIPTION)
            .targetPlanVersionId(null)                // no target → should fail
            .effectiveAt(Instant.now().minusSeconds(10))
            .status(SubscriptionScheduleStatus.PENDING)
            .createdBy(UUID.randomUUID()).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(scheduleRepository.findPendingBySubscriptionIdAndActionType(SUB_ID, SubscriptionScheduleActionType.DOWNGRADE_SUBSCRIPTION))
            .thenReturn(Optional.of(s));

        executor.executeOne(s);

        ArgumentCaptor<SubscriptionSchedule> cap = ArgumentCaptor.forClass(SubscriptionSchedule.class);
        verify(scheduleRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(SubscriptionScheduleStatus.FAILED);
        verify(commercialEngineService, never()).applyScheduledPlanChange(any(), any(), any(), any());
    }

    // ── CHANGE_BILLING_CYCLE (not implemented) ────────────────────────────────

    @Test
    void executeOne_changeBillingCycle_marksFailedWithDocumentedReason() {
        SubscriptionSchedule s = schedule(SubscriptionScheduleActionType.CHANGE_BILLING_CYCLE);
        when(scheduleRepository.findPendingBySubscriptionIdAndActionType(SUB_ID, SubscriptionScheduleActionType.CHANGE_BILLING_CYCLE))
            .thenReturn(Optional.of(s));

        executor.executeOne(s);

        ArgumentCaptor<SubscriptionSchedule> cap = ArgumentCaptor.forClass(SubscriptionSchedule.class);
        verify(scheduleRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(SubscriptionScheduleStatus.FAILED);
        assertThat(cap.getValue().getFailureReason()).contains("not yet implemented");
        // Must not touch subscriptionService or commercialEngineService
        verify(subscriptionService, never()).cancelSubscriptionById(any(), any(), any(), any(), anyBoolean());
        verify(commercialEngineService, never()).applyScheduledPlanChange(any(), any(), any(), any());
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    void executeOne_scheduleNolongerPending_isSkipped() {
        SubscriptionSchedule s = schedule(SubscriptionScheduleActionType.CANCEL_SUBSCRIPTION);
        when(scheduleRepository.findPendingBySubscriptionIdAndActionType(SUB_ID, SubscriptionScheduleActionType.CANCEL_SUBSCRIPTION))
            .thenReturn(Optional.empty());  // already executed or cancelled

        executor.executeOne(s);

        verify(subscriptionService, never()).cancelSubscriptionById(any(), any(), any(), any(), anyBoolean());
        verify(scheduleRepository, never()).save(any());
    }
}
