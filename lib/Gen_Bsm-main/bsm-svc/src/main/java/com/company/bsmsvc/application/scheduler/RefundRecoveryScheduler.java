package com.company.bsmsvc.application.scheduler;

import com.company.bsmsvc.application.service.RefundRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRecoveryScheduler {

    private final RefundRecoveryService refundRecoveryService;

    @Scheduled(fixedDelayString = "${refund.recovery.interval-ms:300000}")
    public void runRecovery() {
        log.debug("RefundRecoveryScheduler: starting recovery run");
        try {
            refundRecoveryService.recoverAll();
        } catch (Exception e) {
            log.error("RefundRecoveryScheduler: recovery run failed", e);
        }
    }
}
