package com.company.bsmsvc.application.scheduler;

import com.company.bsmsvc.application.service.PaymentReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationScheduler {

    private final PaymentReconciliationService reconciliationService;

    @Scheduled(fixedDelayString = "${payment.reconciliation.interval-ms:300000}")
    public void runReconciliation() {
        log.info("PaymentReconciliationScheduler: starting reconciliation run");
        try {
            reconciliationService.reconcilePendingPayments();
        } catch (Exception e) {
            log.error("PaymentReconciliationScheduler: reconciliation run failed", e);
        }
        log.info("PaymentReconciliationScheduler: reconciliation run complete");
    }
}
