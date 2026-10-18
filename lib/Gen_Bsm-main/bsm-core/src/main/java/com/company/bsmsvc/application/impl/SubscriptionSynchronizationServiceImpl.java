package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.SubscriptionSynchronizationService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import com.company.bsmsvc.domain.event.ExternalSubscriptionCancelledEvent;
import com.company.bsmsvc.domain.event.ExternalSubscriptionCreatedEvent;
import com.company.bsmsvc.domain.event.ExternalSubscriptionUpdatedEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.CancelSubscriptionCommand;
import com.company.bsmsvc.domain.model.payment.CreateSubscriptionCommand;
import com.company.bsmsvc.domain.model.payment.SubscriptionResult;
import com.company.bsmsvc.domain.model.payment.UpdateSubscriptionCommand;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSynchronizationServiceImpl implements SubscriptionSynchronizationService {

    private final TenantBillingProfileService billingProfileService;
    private final PaymentGatewayResolver resolver;
    private final SubscriptionRepositoryPort subscriptionRepository;
    private final SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort;
    private final PaymentMethodRepositoryPort paymentMethodRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Subscription syncCreate(Subscription subscription) {
        // Re-read the subscription from DB inside this new transaction before calling Stripe.
        // REQUIRES_NEW gives us a fresh EntityManager, so this reflects the latest committed
        // state.  If another pod already completed syncCreate for the same subscription, the
        // externalSubscriptionId will be non-null here and we skip the Stripe call entirely.
        // This prevents the ProviderSyncRetryScheduler race where two pods both see
        // externalSubscriptionId=null, both create Stripe subscriptions, and the loser's
        // orphaned Stripe subscription silently auto-bills the customer.
        Subscription current = subscriptionRepository.findById(subscription.getId()).orElse(null);
        if (current != null && current.getExternalSubscriptionId() != null) {
            log.info("syncCreate: subscriptionId={} already synced (externalId={}) — idempotent skip",
                subscription.getId(), current.getExternalSubscriptionId());
            return current;
        }

        // PPM-backed subscriptions: provider payment lifecycle is managed by PPM, not BSM
        Subscription authoritative = current != null ? current : subscription;
        if (authoritative.getPpmPlanVersionId() != null) {
            log.debug("syncCreate: subscriptionId={} is PPM-backed — skipping BSM provider sync",
                subscription.getId());
            return authoritative;
        }

        log.debug("syncCreate: subscriptionId={} is BSM-native — provider sync not supported after catalog decommission",
            subscription.getId());
        return subscription;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Subscription syncUpdate(Subscription subscription) {
        if (subscription.getPpmPlanVersionId() != null) {
            log.debug("syncUpdate: subscriptionId={} is PPM-backed — skipping BSM provider sync",
                subscription.getId());
            return subscription;
        }
        if (subscription.getExternalSubscriptionId() == null) {
            log.warn("syncUpdate: no externalSubscriptionId on subscriptionId={} — skipping", subscription.getId());
            return subscription;
        }

        log.debug("syncUpdate: subscriptionId={} is BSM-native — provider sync not supported after catalog decommission",
            subscription.getId());
        return subscription;
    }

    @Override
    @Transactional
    public void syncCancel(Subscription subscription, boolean immediately) {
        if (subscription.getExternalSubscriptionId() == null) {
            log.warn("syncCancel: no externalSubscriptionId on subscriptionId={} — skipping", subscription.getId());
            return;
        }
        // Idempotency: dunning-cancelled subscriptions don't need another provider cancel
        if (subscription.getDunningStatus() == com.company.bsmsvc.domain.enums.DunningStatus.CANCELLED) {
            log.info("syncCancel: already dunning-cancelled subscriptionId={} — skipping duplicate cancel", subscription.getId());
            return;
        }

        TenantBillingProfile profile = resolveProfile(subscription);
        if (profile == null) return;

        PaymentGatewayPort gateway = resolver.resolve(profile.getPaymentProvider());
        gateway.cancelSubscription(new CancelSubscriptionCommand(
            subscription.getExternalSubscriptionId(), immediately
        ));

        subscription.registerEvent(new ExternalSubscriptionCancelledEvent(
            subscription.getId(), subscription.getTenantId(),
            subscription.getExternalSubscriptionId(), profile.getPaymentProvider(), immediately, Instant.now()
        ));

        subscriptionRepository.save(subscription);
        recordHistory(subscription, SubscriptionHistoryAction.EXTERNAL_SUBSCRIPTION_CANCELLED,
            "External subscription cancelled via " + profile.getPaymentProvider() + " immediately=" + immediately);
        log.info("syncCancel: SUCCESS subscriptionId={} immediately={}", subscription.getId(), immediately);
    }

    private TenantBillingProfile resolveProfile(Subscription subscription) {
        try {
            TenantBillingProfile profile = billingProfileService.getProfile(subscription.getTenantId());
            if (profile.getExternalCustomerId() == null || profile.getExternalCustomerId().isBlank()) {
                log.warn("sync: no externalCustomerId for tenantId={} — skipping provider sync",
                    subscription.getTenantId());
                return null;
            }
            return profile;
        } catch (TenantBillingProfileNotFoundException e) {
            log.warn("sync: no billing profile for tenantId={} — skipping provider sync",
                subscription.getTenantId());
            return null;
        }
    }

    private void recordHistory(Subscription subscription, SubscriptionHistoryAction action, String reason) {
        subscriptionHistoryRepositoryPort.save(SubscriptionHistory.builder()
            .id(UUID.randomUUID())
            .subscriptionId(subscription.getId())
            .tenantId(subscription.getTenantId())
            .action(action)
            .fromPlanVersionId(subscription.getPlanVersionId())
            .toPlanVersionId(subscription.getPlanVersionId())
            .reason(reason)
            .performedBy("SYSTEM")
            .actorId(null)
            .actorType(ActorType.SYSTEM)
            .occurredAt(Instant.now())
            .build());
    }
}
