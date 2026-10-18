package com.company.bsmsvc.application.scheduler;

import com.company.bsmsvc.application.impl.TrialExpiryServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically transitions expired TRIALING subscriptions to ACTIVE.
 *
 * <p>A subscription is expired when {@code trialEndsAt <= NOW()}. After transition
 * the subscription enters normal billing: {@link InvoiceRenewalScheduler} generates
 * the first paid invoice on the next run, and dunning starts if payment fails.
 *
 * <p>Default interval: 1 hour. Override with
 * {@code bsm.trial.expiry-check-interval-ms}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrialExpiryScheduler {

    private final TrialExpiryServiceImpl trialExpiryService;

    @Scheduled(fixedDelayString = "${bsm.trial.expiry-check-interval-ms:3600000}")
    public void checkExpiredTrials() {
        log.debug("[TrialExpiryScheduler] checking for expired trials");
        try {
            trialExpiryService.processExpiredTrials();
        } catch (Exception e) {
            log.error("[TrialExpiryScheduler] run failed: {}", e.getMessage(), e);
        }
    }
}
