package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.CommercialEngineService;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.domain.enums.SubscriptionScheduleStatus;
import com.company.bsmsvc.domain.model.SubscriptionSchedule;
import com.company.bsmsvc.domain.port.SubscriptionScheduleRepositoryPort;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes subscription schedules whose {@code effectiveAt <= NOW()}.
 *
 * <h3>Supported action types</h3>
 * <ul>
 *   <li>{@code CANCEL_SUBSCRIPTION} — immediately cancels the subscription.</li>
 *   <li>{@code DOWNGRADE_SUBSCRIPTION} — applies the plan change to the target plan version.</li>
 *   <li>{@code CHANGE_BILLING_CYCLE} — <b>not yet implemented</b>; schedule is marked FAILED
 *       with a descriptive reason. No automatic retry — requires a future implementation.</li>
 * </ul>
 *
 * <p>Each schedule is executed in its own {@code @Transactional} scope so a failure
 * for one subscription does not roll back completed executions for others.</p>
 *
 * <p>Idempotency is provided by the EXECUTED/FAILED terminal status: the scheduler
 * query only returns PENDING schedules, so processed schedules are never retried.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionScheduleExecutorServiceImpl {

    private final SubscriptionScheduleRepositoryPort scheduleRepository;
    private final SubscriptionService subscriptionService;
    private final CommercialEngineService commercialEngineService;

    public void executeDueSchedules() {
        Instant now = Instant.now();
        List<SubscriptionSchedule> due = scheduleRepository.findDueSchedules(now);
        if (due.isEmpty()) {
            log.debug("[ScheduleExecutor] no due schedules at {}", now);
            return;
        }
        log.info("[ScheduleExecutor] {} schedules due for execution", due.size());
        for (SubscriptionSchedule schedule : due) {
            try {
                executeOne(schedule);
            } catch (Exception e) {
                log.error("[ScheduleExecutor] failed for scheduleId={} actionType={} subscriptionId={}: {}",
                    schedule.getId(), schedule.getActionType(), schedule.getSubscriptionId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void executeOne(SubscriptionSchedule schedule) {
        // Reload inside transaction to obtain the current version and guard against races
        SubscriptionSchedule current = scheduleRepository
            .findPendingBySubscriptionIdAndActionType(schedule.getSubscriptionId(), schedule.getActionType())
            .orElse(null);

        if (current == null) {
            log.info("[ScheduleExecutor] schedule no longer PENDING — may have been cancelled or already executed. scheduleId={}",
                schedule.getId());
            return;
        }

        log.info("[ScheduleExecutor] executing scheduleId={} actionType={} subscriptionId={} effectiveAt={}",
            current.getId(), current.getActionType(), current.getSubscriptionId(), current.getEffectiveAt());

        try {
            switch (current.getActionType()) {
                case CANCEL_SUBSCRIPTION -> executeCancellation(current);
                case DOWNGRADE_SUBSCRIPTION -> executeDowngrade(current);
                case CHANGE_BILLING_CYCLE -> {
                    // CHANGE_BILLING_CYCLE is not yet implemented — no service method exists.
                    // Mark FAILED so the operator is alerted; do not silently drop.
                    String reason = "CHANGE_BILLING_CYCLE execution not yet implemented";
                    log.warn("[ScheduleExecutor] {} scheduleId={}", reason, current.getId());
                    markFailed(current, reason);
                    return;
                }
            }
            markExecuted(current);
        } catch (Exception e) {
            log.error("[ScheduleExecutor] execution error scheduleId={}: {}", current.getId(), e.getMessage(), e);
            markFailed(current, e.getMessage());
        }
    }

    private void executeCancellation(SubscriptionSchedule schedule) {
        // cancelSubscriptionById with cancelAtPeriodEnd=false performs immediate cancellation.
        // The schedule was created for end-of-period cancellation, so this fires at effectiveAt
        // (which equals the period end) and immediately cancels.
        subscriptionService.cancelSubscriptionById(
            schedule.getSubscriptionId(),
            schedule.getTenantId(),
            "Scheduled cancellation executed at effectiveAt=" + schedule.getEffectiveAt(),
            "SCHEDULE_EXECUTOR",
            /* cancelAtPeriodEnd = */ false
        );
        log.info("[ScheduleExecutor] CANCEL_SUBSCRIPTION executed subscriptionId={}", schedule.getSubscriptionId());
    }

    private void executeDowngrade(SubscriptionSchedule schedule) {
        if (schedule.getTargetPlanVersionId() == null) {
            throw new IllegalStateException(
                "DOWNGRADE_SUBSCRIPTION schedule has no targetPlanVersionId: " + schedule.getId());
        }
        commercialEngineService.applyScheduledPlanChange(
            schedule.getSubscriptionId(),
            schedule.getTenantId(),
            schedule.getTargetPlanVersionId(),
            "Scheduled downgrade executed at effectiveAt=" + schedule.getEffectiveAt()
        );
        log.info("[ScheduleExecutor] DOWNGRADE_SUBSCRIPTION executed subscriptionId={} targetPlanVersionId={}",
            schedule.getSubscriptionId(), schedule.getTargetPlanVersionId());
    }

    private void markExecuted(SubscriptionSchedule schedule) {
        scheduleRepository.save(schedule.toBuilder()
            .status(SubscriptionScheduleStatus.EXECUTED)
            .executedAt(Instant.now())
            .executedBy("SCHEDULE_EXECUTOR")
            .build());
    }

    private void markFailed(SubscriptionSchedule schedule, String reason) {
        scheduleRepository.save(schedule.toBuilder()
            .status(SubscriptionScheduleStatus.FAILED)
            .executedAt(Instant.now())
            .executedBy("SCHEDULE_EXECUTOR")
            .failureReason(reason)
            .build());
    }
}
