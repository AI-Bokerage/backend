package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.DunningService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.domain.port.TenantScopePort;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
import com.company.bsmsvc.domain.port.DunningEventPublisher;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.model.DunningPolicy;
import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.DunningAttemptStatus;
import com.company.bsmsvc.domain.enums.DunningStatus;
import com.company.bsmsvc.domain.enums.LedgerEntryType;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.ConcurrentUpdateException;
import com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException;
import com.company.bsmsvc.domain.model.BillingLedgerEntry;
import com.company.bsmsvc.domain.model.DunningAttempt;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.model.payment.RetryPaymentCommand;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.DunningAttemptRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DunningServiceImpl implements DunningService {

    private final DunningEventPublisher dunningEventPublisher;
    private final DunningAttemptRepositoryPort dunningAttemptRepository;
    private final SubscriptionRepositoryPort subscriptionRepository;
    private final SubscriptionHistoryRepositoryPort subscriptionHistoryRepository;
    private final PaymentMethodRepositoryPort paymentMethodRepository;
    private final PaymentRepositoryPort paymentRepository;
    private final BillingLedgerRepositoryPort ledgerRepository;
    private final TenantBillingProfileService billingProfileService;
    private final PaymentGatewayResolver resolver;
    private final InvoiceService invoiceService;
    private final DunningPolicy dunningPolicy;
    private final TenantScopePort tenantScopeEnforcer;
    private final SubscriptionEventPublisherPort subscriptionEventPublisher;
    private final EventPublisherPort auditEventPublisher;

    @Override
    @Transactional
    public void startDunning(UUID subscriptionId, UUID invoiceId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            log.warn("startDunning: subscription not found subscriptionId={}", subscriptionId);
            return;
        }
        if (subscription.isInDunning()) {
            log.info("startDunning: already in dunning subscriptionId={} dunningStatus={}", subscriptionId, subscription.getDunningStatus());
            return;
        }
        // Guard: only ACTIVE and PAST_DUE subscriptions can enter dunning
        var subStatus = subscription.getStatus();
        if (subStatus == com.company.bsmsvc.domain.enums.SubscriptionStatus.PAUSED
            || subStatus == com.company.bsmsvc.domain.enums.SubscriptionStatus.CANCELLED
            || subStatus == com.company.bsmsvc.domain.enums.SubscriptionStatus.SUSPENDED_PENDING_PURGE) {
            log.info("startDunning: skipping dunning — subscription status={} subscriptionId={}", subStatus, subscriptionId);
            return;
        }

        DunningPolicy policy = dunningPolicy;
        Instant nextRetry = Instant.now().plus(policy.day1RetryAfterHours(), ChronoUnit.HOURS);

        subscription.startDunning(invoiceId, nextRetry);
        subscriptionRepository.save(subscription);

        DunningAttempt attempt = DunningAttempt.builder()
            .id(UUID.randomUUID()).subscriptionId(subscriptionId).tenantId(subscription.getTenantId())
            .invoiceId(invoiceId).attemptNumber(1).status(DunningAttemptStatus.PENDING)
            .nextRetryAt(nextRetry).createdAt(Instant.now()).build();
        dunningAttemptRepository.save(attempt);

        saveHistory(subscription, SubscriptionHistoryAction.DUNNING_STARTED, "Dunning started: payment failed");
        dunningEventPublisher.publishStarted(subscriptionId, subscription.getTenantId(), invoiceId, 1);
        auditEventPublisher.publish("bsm.dunning.started", subscription.getTenantId(),
            "DunningAttempt", attempt.getId(), null, dunningAuditData(subscriptionId, invoiceId, 1, null));
        log.info("startDunning: dunning started subscriptionId={} invoiceId={} nextRetry={}", subscriptionId, invoiceId, nextRetry);
    }

    @Override
    public void processDueAttempts() {
        List<DunningAttempt> due = dunningAttemptRepository.findDuePending(Instant.now());
        log.info("processDueAttempts: found {} due attempts", due.size());
        for (DunningAttempt attempt : due) {
            try {
                executeAttempt(attempt);
            } catch (ConcurrentUpdateException e) {
                // Another scheduler pod already processed this attempt — not an error, just skip.
                log.info("processDueAttempts: attempt already processed by another node, skipping attemptId={}", attempt.getId());
            } catch (Exception e) {
                log.error("processDueAttempts: failed for attemptId={} subscriptionId={}", attempt.getId(), attempt.getSubscriptionId(), e);
            }
        }
    }

    @Override
    @Transactional
    public void manualRetry(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new BusinessRuleViolationException("Subscription not found: " + subscriptionId));
        tenantScopeEnforcer.assertTenantAccess(subscription.getTenantId());
        DunningAttempt latest = dunningAttemptRepository.findLatestBySubscriptionId(subscriptionId)
            .orElseThrow(() -> new BusinessRuleViolationException("No dunning attempts for subscription: " + subscriptionId));
        if (latest.getStatus() != DunningAttemptStatus.PENDING && latest.getStatus() != DunningAttemptStatus.FAILED) {
            throw new BusinessRuleViolationException("No retryable dunning attempt found");
        }
        DunningAttempt retriable = latest.toBuilder().nextRetryAt(Instant.now()).status(DunningAttemptStatus.PENDING).build();
        dunningAttemptRepository.save(retriable);
        executeAttempt(retriable);
    }

    @Override
    public DunningAttempt getLatestAttempt(UUID subscriptionId) {
        return dunningAttemptRepository.findLatestBySubscriptionId(subscriptionId).orElse(null);
    }

    @Override
    public List<DunningAttempt> getAttempts(UUID subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new com.company.bsmsvc.domain.exception.SubscriptionNotFoundException(
                "Subscription not found: " + subscriptionId));
        tenantScopeEnforcer.assertTenantAccess(subscription.getTenantId());
        return dunningAttemptRepository.findBySubscriptionId(subscriptionId);
    }

    @Transactional
    public void executeAttempt(DunningAttempt attempt) {
        Subscription subscription = subscriptionRepository.findById(attempt.getSubscriptionId()).orElse(null);
        if (subscription == null || !subscription.isInDunning()) {
            // Subscription already recovered or cancelled — mark stale attempt resolved so it stops retrying.
            dunningAttemptRepository.save(attempt.toBuilder()
                .status(DunningAttemptStatus.SUCCEEDED)
                .failureMessage("Dunning resolved by another attempt")
                .attemptedAt(Instant.now())
                .build());
            log.info("executeAttempt: subscription not in dunning, marking stale attempt resolved attemptId={}", attempt.getId());
            return;
        }

        // Hard guard: never charge a CANCELLED subscription.
        // This is a second line of defence after cancelAndClearDunning() clears dunning fields.
        // A race between the webhook cancellation and the dunning scheduler can still deliver
        // both concurrent changes — this guard is the final safety net.
        if (subscription.getStatus() == com.company.bsmsvc.domain.enums.SubscriptionStatus.CANCELLED) {
            dunningAttemptRepository.save(attempt.toBuilder()
                .status(DunningAttemptStatus.SUCCEEDED)
                .failureMessage("Subscription is CANCELLED — dunning attempt terminated without retry")
                .attemptedAt(Instant.now())
                .build());
            log.info("executeAttempt: subscription CANCELLED — terminating dunning attempt without payment call attemptId={} subscriptionId={}",
                attempt.getId(), attempt.getSubscriptionId());
            return;
        }

        // Attempt #4+ is a lifecycle action (cancellation), not a payment retry
        if (subscription.getDunningStatus() == DunningStatus.SUSPENDED_PENDING_PURGE) {
            log.info("executeAttempt: cancellation attempt subscriptionId={}", attempt.getSubscriptionId());
            dunningAttemptRepository.save(attempt.toBuilder().status(DunningAttemptStatus.SUCCEEDED).attemptedAt(Instant.now()).build());
            subscription.cancelFromDunning(Instant.now());
            subscriptionRepository.save(subscription);
            saveHistory(subscription, SubscriptionHistoryAction.DUNNING_CANCELLED, "Subscription cancelled after suspension grace period");
            return;
        }

        // Mark attempt in-progress before API call to prevent double execution.
        // Reload after save to get the DB-incremented @Version — the returned object
        // from save() is pre-commit, so using it for the next save causes OL exception.
        UUID attemptId = attempt.getId();
        dunningAttemptRepository.save(attempt.toBuilder().status(DunningAttemptStatus.IN_PROGRESS).attemptedAt(Instant.now()).build());
        attempt = dunningAttemptRepository.findById(attemptId).orElse(attempt);

        String defaultPmId = paymentMethodRepository.findDefaultByTenantId(attempt.getTenantId())
            .map(pm -> pm.getExternalPaymentMethodId()).orElse(null);

        if (defaultPmId == null) {
            log.warn("executeAttempt: no default PM for tenantId={} — skipping retry", attempt.getTenantId());
            advanceDunningAfterFailure(attempt, subscription, "No default payment method available", null);
            return;
        }

        com.company.bsmsvc.domain.model.PlatformInvoice invoice;
        try {
            invoice = invoiceService.getInvoiceById(attempt.getInvoiceId());
        } catch (Exception e) {
            log.warn("executeAttempt: invoice not found invoiceId={}", attempt.getInvoiceId());
            advanceDunningAfterFailure(attempt, subscription, "Invoice not found", null);
            return;
        }

        try {
            var profile = billingProfileService.getProfile(attempt.getTenantId());
            PaymentGatewayPort gateway = resolver.resolve(profile.getPaymentProvider());
            var result = gateway.retryPayment(new RetryPaymentCommand(
                profile.getExternalCustomerId(), defaultPmId,
                invoice.getAmountDue(),
                invoice.getCurrency(),
                Map.of("subscriptionId", attempt.getSubscriptionId().toString(),
                       "invoiceId", attempt.getInvoiceId().toString(),
                       "dunningAttempt", String.valueOf(attempt.getAttemptNumber())),
                // Deterministic key: same attempt ID always maps to the same Stripe PI
                "dunning-attempt-" + attempt.getId().toString()
            ));

            if ("succeeded".equals(result.status())) {
                handleRetrySuccess(attempt, subscription, result.paymentIntentId(), invoice.getCurrency());
            } else {
                advanceDunningAfterFailure(attempt, subscription, "Payment status: " + result.status(), result.paymentIntentId());
            }
        } catch (com.company.bsmsvc.domain.exception.PaymentGatewayException e) {
            advanceDunningAfterFailure(attempt, subscription, e.getMessage(), null);
        } catch (TenantBillingProfileNotFoundException e) {
            log.warn("executeAttempt: no billing profile tenantId={}", attempt.getTenantId());
            advanceDunningAfterFailure(attempt, subscription, "No billing profile", null);
        }
    }

    private void handleRetrySuccess(DunningAttempt attempt, Subscription subscription, String paymentIntentId, String currency) {
        dunningAttemptRepository.save(attempt.toBuilder()
            .status(DunningAttemptStatus.SUCCEEDED).attemptedAt(Instant.now())
            .externalPaymentId(paymentIntentId).build());

        paymentRepository.findByInvoiceId(attempt.getInvoiceId()).stream()
            .filter(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.FAILED)
            .findFirst().ifPresent(p -> {
                p.markSucceeded(paymentIntentId);
                paymentRepository.save(p);
            });

        // applyPayment() calls invoice.markPaid() which fires InvoiceMarkedPaidEvent.
        // PlatformInvoiceRepositoryAdapter.save() writes the single authoritative INVOICE_PAID
        // ledger entry in response to that event.  Never write a second explicit entry here.
        try {
            invoiceService.applyPayment(attempt.getInvoiceId(), null, null);
        } catch (Exception e) {
            log.warn("handleRetrySuccess: could not apply payment invoiceId={}: {}", attempt.getInvoiceId(), e.getMessage());
        }

        subscription.recoverFromDunning();
        subscriptionRepository.save(subscription);
        saveHistory(subscription, SubscriptionHistoryAction.DUNNING_RECOVERED, "Payment recovered on attempt #" + attempt.getAttemptNumber());
        dunningEventPublisher.publishRecovered(subscription.getId(), subscription.getTenantId(), attempt.getInvoiceId());
        auditEventPublisher.publish("bsm.dunning.recovered", subscription.getTenantId(),
            "DunningAttempt", attempt.getId(), null,
            dunningAuditData(subscription.getId(), attempt.getInvoiceId(), attempt.getAttemptNumber(), null));
        // Publish subscription.changed so TNT billing snapshot converges immediately
        subscriptionEventPublisher.publishChanged(subscription, null, "dunning-recovery");
        log.info("handleRetrySuccess: recovered subscriptionId={} attempt={}", subscription.getId(), attempt.getAttemptNumber());
    }

    private void advanceDunningAfterFailure(DunningAttempt attempt, Subscription subscription, String reason, String paymentIntentId) {
        dunningAttemptRepository.save(attempt.toBuilder()
            .status(DunningAttemptStatus.FAILED).attemptedAt(Instant.now())
            .failureMessage(reason).externalPaymentId(paymentIntentId).build());

        // Write PAYMENT_FAILED ledger entry so dunning retry failures are auditable.
        // Load the invoice to get the currency and attempted amount.
        try {
            var invoice = invoiceService.getInvoiceById(attempt.getInvoiceId());
            saveLedger(attempt.getTenantId(), attempt.getInvoiceId(), LedgerEntryType.PAYMENT_FAILED,
                invoice.getAmountDue(),
                "Dunning attempt #" + attempt.getAttemptNumber() + " failed: " + reason,
                invoice.getCurrency());
        } catch (Exception e) {
            log.warn("advanceDunningAfterFailure: could not write PAYMENT_FAILED ledger attemptId={}: {}",
                attempt.getId(), e.getMessage());
        }

        DunningPolicy policy = dunningPolicy;
        DunningStatus next = nextStatus(subscription.getDunningStatus());
        Instant nextAction = computeNextActionAt(next, policy);

        subscription.advanceDunningAfterFailure(next, nextAction);
        subscriptionRepository.save(subscription);

        if (next == DunningStatus.SUSPENDED_PENDING_PURGE) {
            // Schedule cancellation
            DunningAttempt cancelAttempt = DunningAttempt.builder()
                .id(UUID.randomUUID()).subscriptionId(subscription.getId()).tenantId(subscription.getTenantId())
                .invoiceId(attempt.getInvoiceId()).attemptNumber(attempt.getAttemptNumber() + 1)
                .status(DunningAttemptStatus.PENDING)
                .nextRetryAt(Instant.now().plus(policy.cancelAfterDays(), ChronoUnit.DAYS))
                .createdAt(Instant.now()).build();
            dunningAttemptRepository.save(cancelAttempt);
            saveHistory(subscription, SubscriptionHistoryAction.SUBSCRIPTION_SUSPENDED, "Suspended after dunning failure");
            dunningEventPublisher.publishSuspended(subscription.getId(), subscription.getTenantId());
            auditEventPublisher.publish("bsm.dunning.suspended", subscription.getTenantId(),
                "DunningAttempt", cancelAttempt.getId(), null,
                dunningAuditData(subscription.getId(), attempt.getInvoiceId(), attempt.getAttemptNumber(), reason));
            subscriptionEventPublisher.publishExpired(subscription);
        } else if (next == DunningStatus.CANCELLED) {
            subscription.cancelFromDunning(Instant.now());
            subscriptionRepository.save(subscription);
            saveHistory(subscription, SubscriptionHistoryAction.DUNNING_CANCELLED, "Cancelled after suspension grace period");
            dunningEventPublisher.publishCancelled(subscription.getId(), subscription.getTenantId());
            auditEventPublisher.publish("bsm.dunning.cancelled", subscription.getTenantId(),
                "DunningAttempt", attempt.getId(), null,
                dunningAuditData(subscription.getId(), attempt.getInvoiceId(), attempt.getAttemptNumber(), reason));
            subscriptionEventPublisher.publishCanceled(subscription);
        } else {
            // Schedule next retry
            DunningAttempt nextAttempt = DunningAttempt.builder()
                .id(UUID.randomUUID()).subscriptionId(subscription.getId()).tenantId(subscription.getTenantId())
                .invoiceId(attempt.getInvoiceId()).attemptNumber(attempt.getAttemptNumber() + 1)
                .status(DunningAttemptStatus.PENDING).nextRetryAt(nextAction).createdAt(Instant.now()).build();
            dunningAttemptRepository.save(nextAttempt);
            saveHistory(subscription, SubscriptionHistoryAction.DUNNING_RETRY, "Retry #" + nextAttempt.getAttemptNumber() + " scheduled: " + reason);
            dunningEventPublisher.publishRetry(subscription.getId(), subscription.getTenantId(), nextAttempt.getAttemptNumber(), next);
            auditEventPublisher.publish("bsm.dunning.retry", subscription.getTenantId(),
                "DunningAttempt", nextAttempt.getId(), null,
                dunningAuditData(subscription.getId(), attempt.getInvoiceId(), nextAttempt.getAttemptNumber(), reason));
        }
        log.info("advanceDunningAfterFailure: subscriptionId={} attempt={} newStatus={}", subscription.getId(), attempt.getAttemptNumber(), next);
    }

    private DunningStatus nextStatus(DunningStatus current) {
        if (current == null) return DunningStatus.DUNNING_DAY_1;
        return switch (current) {
            case DUNNING_DAY_1 -> DunningStatus.DUNNING_DAY_3;
            case DUNNING_DAY_3 -> DunningStatus.DUNNING_DAY_7;
            case DUNNING_DAY_7 -> DunningStatus.SUSPENDED_PENDING_PURGE;
            case SUSPENDED_PENDING_PURGE -> DunningStatus.CANCELLED;
            default -> DunningStatus.CANCELLED;
        };
    }

    private Instant computeNextActionAt(DunningStatus next, DunningPolicy policy) {
        return switch (next) {
            case DUNNING_DAY_3 -> Instant.now().plus(policy.day3RetryAfterHours(), ChronoUnit.HOURS);
            case DUNNING_DAY_7 -> Instant.now().plus(policy.day7RetryAfterHours(), ChronoUnit.HOURS);
            case SUSPENDED_PENDING_PURGE -> Instant.now().plus(policy.suspendAfterDays(), ChronoUnit.DAYS);
            case CANCELLED -> Instant.now().plus(policy.cancelAfterDays(), ChronoUnit.DAYS);
            default -> Instant.now().plus(24, ChronoUnit.HOURS);
        };
    }

    @Override
    @Transactional
    public void recoveryPaymentReceived(UUID subscriptionId, UUID invoiceId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null || !subscription.isInDunning()) {
            log.debug("recoveryPaymentReceived: subscriptionId={} is not in dunning — no action", subscriptionId);
            return;
        }

        // Mark the latest active dunning attempt resolved so the scheduler stops retrying
        dunningAttemptRepository.findLatestBySubscriptionId(subscriptionId).ifPresent(attempt -> {
            if (attempt.getStatus() == DunningAttemptStatus.PENDING
                    || attempt.getStatus() == DunningAttemptStatus.IN_PROGRESS
                    || attempt.getStatus() == DunningAttemptStatus.FAILED) {
                dunningAttemptRepository.save(attempt.toBuilder()
                    .status(DunningAttemptStatus.SUCCEEDED)
                    .failureMessage("Payment received via external channel — dunning resolved")
                    .attemptedAt(Instant.now())
                    .build());
            }
        });

        subscription.recoverFromDunning();
        subscriptionRepository.save(subscription);
        saveHistory(subscription, SubscriptionHistoryAction.DUNNING_RECOVERED,
            "Payment received via external channel (webhook/checkout)");
        dunningEventPublisher.publishRecovered(subscription.getId(), subscription.getTenantId(), invoiceId);
        auditEventPublisher.publish("bsm.dunning.recovered", subscription.getTenantId(),
            "DunningAttempt", subscription.getId(), null,
            dunningAuditData(subscription.getId(), invoiceId, null, "external-payment"));
        subscriptionEventPublisher.publishChanged(subscription, null, "dunning-recovery-external");
        log.info("recoveryPaymentReceived: subscriptionId={} recovered from dunning via external payment", subscriptionId);
    }

    private Map<String, Object> dunningAuditData(UUID subscriptionId, UUID invoiceId, Integer attemptNumber, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("subscriptionId", subscriptionId.toString());
        data.put("invoiceId", invoiceId != null ? invoiceId.toString() : null);
        data.put("attemptNumber", attemptNumber);
        data.put("reason", reason);
        return data;
    }

    private void saveHistory(Subscription sub, SubscriptionHistoryAction action, String reason) {
        subscriptionHistoryRepository.save(SubscriptionHistory.builder()
            .id(UUID.randomUUID()).subscriptionId(sub.getId()).tenantId(sub.getTenantId())
            .action(action).fromPlanVersionId(sub.getPlanVersionId()).toPlanVersionId(sub.getPlanVersionId())
            .reason(reason).performedBy("DUNNING_ENGINE").actorId(null).actorType(ActorType.SYSTEM)
            .occurredAt(Instant.now()).build());
    }

    private void saveLedger(UUID tenantId, UUID invoiceId, LedgerEntryType type, long amount, String description, String currency) {
        ledgerRepository.save(BillingLedgerEntry.builder()
            .id(UUID.randomUUID()).tenantId(tenantId).invoiceId(invoiceId)
            .entryType(type).amountMinor(amount).currency(currency)
            .description(description).createdAt(Instant.now()).build());
    }
}
