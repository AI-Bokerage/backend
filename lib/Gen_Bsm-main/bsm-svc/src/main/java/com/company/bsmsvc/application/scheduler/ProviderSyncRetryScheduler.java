package com.company.bsmsvc.application.scheduler;

import com.company.bsmsvc.application.service.SubscriptionSynchronizationService;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries provider subscription creation for BSM subscriptions that have no
 * {@code externalSubscriptionId} — meaning {@code syncCreate()} failed or was never
 * attempted (e.g. Stripe was unavailable when the subscription was first committed).
 *
 * <p>The scheduler runs every 10 minutes by default.  On each run it finds all live
 * (TRIALING / ACTIVE / PAUSED / PAST_DUE / SUSPENDED_PENDING_PURGE) subscriptions
 * with a null {@code externalSubscriptionId} and re-attempts {@code syncCreate()}.
 *
 * <p>Override the interval with {@code bsm.provider-sync.retry-interval-ms}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProviderSyncRetryScheduler {

    private final SubscriptionRepositoryPort subscriptionRepository;
    private final SubscriptionSynchronizationService subscriptionSynchronizationService;

    @Scheduled(fixedDelayString = "${bsm.provider-sync.retry-interval-ms:600000}")
    public void retryOrphanedSyncs() {
        List<Subscription> orphaned = subscriptionRepository.findPendingProviderSync();
        if (orphaned.isEmpty()) {
            log.debug("[ProviderSyncRetry] no orphaned provider syncs");
            return;
        }
        log.warn("[ProviderSyncRetry] found {} subscription(s) with no externalSubscriptionId — retrying provider sync",
            orphaned.size());

        for (Subscription sub : orphaned) {
            try {
                subscriptionSynchronizationService.syncCreate(sub);
                log.info("[ProviderSyncRetry] SUCCESS subscriptionId={} tenantId={}",
                    sub.getId(), sub.getTenantId());
            } catch (Exception e) {
                log.error("[ProviderSyncRetry] FAILED subscriptionId={} tenantId={}: {}",
                    sub.getId(), sub.getTenantId(), e.getMessage());
                // Will retry on next scheduler run — no permanent failure path
            }
        }
    }
}
