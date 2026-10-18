package com.company.bsmsvc.application.scheduler;

import com.company.bsmsvc.application.service.DunningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DunningScheduler {

    private final DunningService dunningService;

    @Scheduled(fixedDelayString = "${dunning.scheduler.retry-interval-ms:300000}")
    public void runDunningRetries() {
        log.info("DunningScheduler: processing due dunning attempts");
        try {
            dunningService.processDueAttempts();
        } catch (Exception e) {
            log.error("DunningScheduler: error processing due attempts", e);
        }
    }
}
