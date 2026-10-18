package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import com.company.bsmsvc.domain.enums.SubscriptionEventType;
import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.exception.ConcurrentUpdateException;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detects and transitions TRIALING subscriptions whose trial period has ended.
 *
 * <p>A subscription is eligible for expiry when:
 * {@code status = TRIALING AND trialEndsAt <= NOW()}
 *
 * <p>On expiry, the subscription is moved to ACTIVE so that the
 * {@link InvoiceRenewalServiceImpl} can generate the first paid invoice on the
 * next scheduler run. If payment collection subsequently fails, the dunning
 * process starts through the normal payment failure path.
 *
 * <p>Each expiry is processed in its own transaction so a failure for one
 * subscription does not roll back successful transitions for others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrialExpiryServiceImpl {

    private final SubscriptionRepositoryPort subscriptionRepository;
    private final com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort subscriptionHistoryRepository;
    private final com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort subscriptionEventRepository;
    private final SubscriptionEventPublisherPort subscriptionEventPublisher;
    private final com.company.bsmsvc.domain.service.SubscriptionLifecycleMapper lifecycleMapper;

    public void processExpiredTrials() {
        Instant now = Instant.now();
        List<Subscription> expired = subscriptionRepository.findExpiredTrials(now);
        if (expired.isEmpty()) {
            log.debug("[TrialExpiry] no expired trials at {}", now);
            return;
        }
        log.info("[TrialExpiry] {} expired trials found", expired.size());
        for (Subscription sub : expired) {
            try {
                expireOne(sub);
            } catch (ConcurrentUpdateException e) {
                // Expected when two scheduler pods process the same expired trial concurrently.
                // One pod wins; the other sees a stale version and is safely rejected.
                // The winning pod already transitioned the subscription to ACTIVE.
                log.info("[TrialExpiry] concurrent execution detected for subscriptionId={} — " +
                    "subscription was already processed by another instance",
                    sub.getId());
            } catch (Exception e) {
                log.error("[TrialExpiry] failed for subscriptionId={}: {}", sub.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void expireOne(Subscription subscription) {
        // Reload inside transaction to avoid stale state and handle optimistic locking
        Subscription current = subscriptionRepository.findById(subscription.getId()).orElse(null);
        if (current == null) {
            log.warn("[TrialExpiry] subscription not found subscriptionId={}", subscription.getId());
            return;
        }
        // Double-check — another node may have processed this between query and lock
        if (current.getStatus() != com.company.bsmsvc.domain.enums.SubscriptionStatus.TRIALING) {
            log.info("[TrialExpiry] subscriptionId={} already transitioned to {} — skipping",
                current.getId(), current.getStatus());
            return;
        }

        current.activate();
        Subscription saved = subscriptionRepository.save(current);

        UUID actorId = UUID.nameUUIDFromBytes("TRIAL_EXPIRY_ENGINE".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Instant now = Instant.now();

        subscriptionHistoryRepository.save(
            com.company.bsmsvc.domain.model.SubscriptionHistory.builder()
                .id(UUID.randomUUID())
                .subscriptionId(saved.getId())
                .tenantId(saved.getTenantId())
                .action(SubscriptionHistoryAction.SUBSCRIPTION_ACTIVATED)
                .fromPlanVersionId(saved.getPlanVersionId())
                .toPlanVersionId(saved.getPlanVersionId())
                .reason("Trial period ended — subscription activated")
                .performedBy("TRIAL_EXPIRY_ENGINE")
                .actorId(actorId)
                .actorType(ActorType.SYSTEM)
                .occurredAt(now)
                .build()
        );

        subscriptionEventRepository.save(
            lifecycleMapper.toEvent(saved, SubscriptionEventType.SUBSCRIPTION_ACTIVATED,
                java.util.Map.of("reason", "trial_expired"), actorId, ActorType.SYSTEM, now)
        );

        subscriptionEventPublisher.publishChanged(saved, null, "trial-expired");

        log.info("[TrialExpiry] subscriptionId={} tenantId={} transitioned TRIALING→ACTIVE",
            saved.getId(), saved.getTenantId());
    }
}
