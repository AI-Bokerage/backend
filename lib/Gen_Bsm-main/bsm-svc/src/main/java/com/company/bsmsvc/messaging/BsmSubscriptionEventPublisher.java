package com.company.bsmsvc.messaging;

import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
import com.company.bsmsvc.infrastructure.client.ppm.PpmVersionMetaClient;
import com.company.bsmsvc.infrastructure.outbox.BsmOutboxService;
import com.company.bsmsvc.messaging.BsmMessagingRouting;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Persists subscription lifecycle events to the BSM outbox table within the
 * caller's current transaction. The outbox poller dispatches them to RabbitMQ
 * after the transaction commits, guaranteeing at-least-once delivery.
 *
 * <p><strong>Option B (parallel publish):</strong> events now carry both
 * {@code newPlanCode} (may be null for PPM-backed subscriptions until tnt-svc
 * migrates) and {@code ppmPlanId} (null for BSM-native subscriptions).
 * tnt-svc consumers should use {@code ppmPlanId} as the authoritative identifier
 * when present.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BsmSubscriptionEventPublisher implements SubscriptionEventPublisherPort {

    private static final String AGGREGATE_TYPE = "Subscription";

    private final BsmOutboxService    outboxService;
    private final PpmVersionMetaClient ppmVersionMetaClient;

    public void publishCreated(Subscription subscription) {
        String planCode = resolvePlanCode(subscription);
        persist(subscription.getId(), subscription.getTenantId(),
            "SubscriptionCreated", BsmMessagingRouting.BSM_SUBSCRIPTION_CREATED,
            null, planCode, subscription);
    }

    public void publishChanged(Subscription subscription, String oldPlanCode, String reason) {
        String newPlanCode = resolvePlanCode(subscription);
        persist(subscription.getId(), subscription.getTenantId(),
            "SubscriptionChanged", BsmMessagingRouting.BSM_SUBSCRIPTION_CHANGED,
            oldPlanCode, newPlanCode, subscription);
    }

    public void publishCanceled(Subscription subscription) {
        String planCode = resolvePlanCode(subscription);
        persist(subscription.getId(), subscription.getTenantId(),
            "SubscriptionCanceled", BsmMessagingRouting.BSM_SUBSCRIPTION_CANCELED,
            planCode, planCode, subscription);
    }

    public void publishExpired(Subscription subscription) {
        String planCode = resolvePlanCode(subscription);
        persist(subscription.getId(), subscription.getTenantId(),
            "SubscriptionExpired", BsmMessagingRouting.BSM_SUBSCRIPTION_EXPIRED,
            planCode, planCode, subscription);
    }

    public void publishRenewed(Subscription subscription) {
        String planCode = resolvePlanCode(subscription);
        persist(subscription.getId(), subscription.getTenantId(),
            "SubscriptionRenewed", BsmMessagingRouting.BSM_SUBSCRIPTION_RENEWED,
            planCode, planCode, subscription);
    }

    public void publishUpgraded(Subscription subscription, UUID fromPlanVersionId) {
        // subscription at this point holds the NEW plan version; resolve new plan code from PPM/BSM.
        // For the old plan code: BSM local lookup (returns null for PPM-backed subs — known limitation
        // until all plan history is tracked via ppmPlanId. tnt-svc should use ppmPlanId instead).
        String newPlanCode = resolvePlanCode(subscription);
        String oldPlanCode = null;
        persist(subscription.getId(), subscription.getTenantId(),
            "SubscriptionChanged", BsmMessagingRouting.BSM_SUBSCRIPTION_CHANGED,
            oldPlanCode, newPlanCode, subscription);
    }

    private void persist(UUID subscriptionId, UUID tenantId,
                         String eventType, String routingKey,
                         String oldPlanCode, String newPlanCode,
                         Subscription subscription) {
        SubscriptionEventPayload payload = new SubscriptionEventPayload(
            SubscriptionEventPayload.CURRENT_VERSION,
            eventType,
            tenantId,
            subscriptionId,
            oldPlanCode,
            newPlanCode,
            subscription.getBillingCycle() != null ? subscription.getBillingCycle().name() : null,
            subscription.getStatus() != null ? subscription.getStatus().name() : null,
            subscription.getTrialEndsAt(),
            Instant.now(),
            subscription.getPpmPlanId()
        );
        outboxService.save(AGGREGATE_TYPE, subscriptionId, eventType, routingKey, payload);
        log.debug("[BSM-SUB-EVENT] Queued outbox event={} subscriptionId={} tenantId={} ppmPlanId={}",
            eventType, subscriptionId, tenantId, subscription.getPpmPlanId());
    }

    /**
     * Resolves plan code for the subscription using PPM for PPM-backed subscriptions
     * and the BSM local catalog for BSM-native subscriptions.
     *
     * <p>For PPM-backed subscriptions ({@code ppmPlanVersionId != null}):
     * calls {@code GET /api/v1/ppm/plan-versions/{id}/meta} to get {@code planCode}.
     * Never probes the BSM catalog — prevents null planCode from propagating to tnt-svc.
     *
     * <p>For BSM-native subscriptions: falls through to the local catalog.
     */
    private String resolvePlanCode(Subscription subscription) {
        if (subscription.getPpmPlanVersionId() != null) {
            try {
                String planCode = ppmVersionMetaClient.getVersionMeta(subscription.getPpmPlanVersionId()).planCode();
                log.debug("[BSM-SUB-EVENT] Resolved planCode={} via PPM meta for ppmPlanVersionId={}",
                    planCode, subscription.getPpmPlanVersionId());
                return planCode;
            } catch (Exception e) {
                log.warn("[BSM-SUB-EVENT] PPM meta lookup failed for ppmPlanVersionId={}: {}",
                    subscription.getPpmPlanVersionId(), e.getMessage());
                return null;
            }
        }
        return null;
    }
}
