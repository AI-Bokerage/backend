package com.company.bsmsvc.application.scheduler;

import com.company.bsmsvc.application.impl.SubscriptionScheduleExecutorServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically executes subscription schedules (downgrades, cancellations) whose
 * {@code effectiveAt} has passed.
 *
 * <p>Default interval: 5 minutes. Override with
 * {@code bsm.schedule.executor-interval-ms}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduleExecutorScheduler {

    private final SubscriptionScheduleExecutorServiceImpl executorService;

    @Scheduled(fixedDelayString = "${bsm.schedule.executor-interval-ms:300000}")
    public void runExecutor() {
        log.debug("[SubscriptionScheduleExecutorScheduler] running");
        try {
            executorService.executeDueSchedules();
        } catch (Exception e) {
            log.error("[SubscriptionScheduleExecutorScheduler] run failed: {}", e.getMessage(), e);
        }
    }
}
