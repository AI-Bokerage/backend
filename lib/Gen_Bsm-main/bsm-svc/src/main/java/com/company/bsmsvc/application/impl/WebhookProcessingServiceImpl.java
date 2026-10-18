package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.DunningService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.WebhookProcessingService;
import com.company.bsmsvc.config.WebhookProperties;
import com.company.bsmsvc.domain.enums.ActorType;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.LedgerEntryType;
import com.company.bsmsvc.domain.enums.PaymentProvider;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.enums.SubscriptionHistoryAction;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.enums.WebhookEventStatus;
import com.company.bsmsvc.domain.exception.WebhookVerificationException;
import com.company.bsmsvc.domain.model.BillingLedgerEntry;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.SubscriptionHistory;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.messaging.BsmSubscriptionEventPublisher;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.WebhookEvent;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.WebhookEventRepositoryPort;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessingServiceImpl implements WebhookProcessingService {

    private final WebhookEventRepositoryPort webhookEventRepository;
    private final PaymentRepositoryPort paymentRepository;
    private final PlatformInvoiceRepositoryPort invoiceRepository;
    private final InvoiceService invoiceService;
    private final BillingLedgerRepositoryPort ledgerRepository;
    private final WebhookProperties webhookProperties;
    private final DunningService dunningService;
    private final SubscriptionRepositoryPort subscriptionRepository;
    private final SubscriptionHistoryRepositoryPort subscriptionHistoryRepository;
    private final BsmSubscriptionEventPublisher subscriptionEventPublisher;

    @Override
    @Transactional
    public void processStripeWebhook(String payload, String signature) {
        long start = System.currentTimeMillis();
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookProperties.stripeSecret());
        } catch (SignatureVerificationException e) {
            throw new WebhookVerificationException("Stripe webhook signature verification failed", e);
        }

        String eventId = event.getId();
        String eventType = event.getType();
        log.info("Stripe webhook received eventId={} type={}", eventId, eventType);

        if (isDuplicate(PaymentProvider.STRIPE, eventId)) {
            log.info("Stripe webhook duplicate ignored: eventId={} type={}", eventId, eventType);
            return;
        }

        WebhookEvent webhookEvent = storeWebhookEvent(PaymentProvider.STRIPE, eventId, eventType, payload);
        try {
            handleStripeEvent(event);
            markProcessed(webhookEvent);
            log.info("Stripe webhook processed eventId={} type={} durationMs={}", eventId, eventType, System.currentTimeMillis() - start);
        } catch (Exception ex) {
            markFailed(webhookEvent, ex.getMessage());
            log.error("Stripe webhook processing failed eventId={} type={} durationMs={} error={}",
                eventId, eventType, System.currentTimeMillis() - start, ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional
    public void processRazorpayWebhook(String payload, String signature) {
        long start = System.currentTimeMillis();
        verifyRazorpaySignature(payload, signature);

        org.json.JSONObject body = new org.json.JSONObject(payload);
        String eventId = body.optString("id", UUID.randomUUID().toString());
        String eventType = body.optString("event", "unknown");
        log.info("Razorpay webhook received eventId={} type={}", eventId, eventType);

        if (isDuplicate(PaymentProvider.RAZORPAY, eventId)) {
            log.info("Razorpay webhook duplicate ignored: eventId={} type={}", eventId, eventType);
            return;
        }

        WebhookEvent webhookEvent = storeWebhookEvent(PaymentProvider.RAZORPAY, eventId, eventType, payload);
        try {
            handleRazorpayEvent(eventType, body);
            markProcessed(webhookEvent);
            log.info("Razorpay webhook processed eventId={} type={} durationMs={}", eventId, eventType, System.currentTimeMillis() - start);
        } catch (Exception ex) {
            markFailed(webhookEvent, ex.getMessage());
            log.error("Razorpay webhook processing failed eventId={} type={} durationMs={} error={}",
                eventId, eventType, System.currentTimeMillis() - start, ex.getMessage(), ex);
        }
    }

    // ── Stripe event routing ──────────────────────────────────────────────────

    private void handleStripeEvent(Event event) {
        switch (event.getType()) {
            case "checkout.session.completed" -> {
                var session = (com.stripe.model.checkout.Session) event.getDataObjectDeserializer().getObject().orElse(null);
                if (session != null) handleCheckoutSessionCompleted(session);
            }
            case "payment_intent.succeeded" -> {
                var intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
                if (intent != null) handlePaymentIntentSucceeded(intent);
            }
            case "payment_intent.payment_failed" -> {
                var intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
                if (intent != null) handlePaymentIntentFailed(intent);
            }
            case "invoice.payment_succeeded" -> {
                var inv = (com.stripe.model.Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
                if (inv != null) handleStripeInvoicePaymentSucceeded(inv);
            }
            case "invoice.payment_failed" -> {
                var inv = (com.stripe.model.Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
                if (inv != null) handleStripeInvoicePaymentFailed(inv);
            }
            case "customer.subscription.deleted" -> {
                var stripeSub = (com.stripe.model.Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                if (stripeSub != null) reconcileSubscriptionDeleted(stripeSub.getId());
            }
            case "customer.subscription.updated" -> {
                var stripeSub = (com.stripe.model.Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
                if (stripeSub != null) reconcileSubscriptionUpdated(stripeSub.getId(), stripeSub.getStatus());
            }
            case "charge.refunded" -> {
                var charge = (com.stripe.model.Charge) event.getDataObjectDeserializer().getObject().orElse(null);
                if (charge != null) reconcileChargeRefunded(
                    charge.getId(),
                    charge.getPaymentIntent(),
                    charge.getAmountRefunded() != null ? charge.getAmountRefunded() : 0L,
                    charge.getCurrency()
                );
            }
            default -> log.debug("Stripe event ignored: type={}", event.getType());
        }
    }

    private void handleCheckoutSessionCompleted(com.stripe.model.checkout.Session session) {
        UUID invoiceId = extractInvoiceId(session.getMetadata());
        Payment payment = resolvePayment(session.getId(), invoiceId, "checkout.session.completed");
        if (payment == null) return;
        payment.markSucceeded(session.getPaymentIntent());
        paymentRepository.save(payment);
        markInvoicePaid(payment);
        log.info("checkout.session.completed sessionId={} invoiceId={}", session.getId(), payment.getInvoiceId());
    }

    private void handlePaymentIntentSucceeded(PaymentIntent intent) {
        UUID invoiceId = extractInvoiceId(intent.getMetadata());
        Payment payment = resolvePayment(intent.getId(), invoiceId, "payment_intent.succeeded");
        if (payment == null) return;
        payment.markSucceeded(intent.getLatestCharge());
        paymentRepository.save(payment);
        markInvoicePaid(payment);
        log.info("payment_intent.succeeded intentId={} invoiceId={}", intent.getId(), payment.getInvoiceId());
    }

    private void handlePaymentIntentFailed(PaymentIntent intent) {
        UUID invoiceId = extractInvoiceId(intent.getMetadata());
        Payment payment = resolvePayment(intent.getId(), invoiceId, "payment_intent.payment_failed");
        if (payment == null) return;
        String reason = intent.getLastPaymentError() != null ? intent.getLastPaymentError().getMessage() : "Unknown";
        payment.markFailed(reason);
        paymentRepository.save(payment);
        createLedgerEntry(payment, LedgerEntryType.ADJUSTMENT, 0, "Payment failed: " + reason);
        log.info("payment_intent.payment_failed intentId={} reason={}", intent.getId(), reason);
        triggerDunning(payment);
    }

    // ── Stripe invoice handlers — auto-billing renewal path ───────────────────

    /**
     * Handles both BSM-initiated payments (Payment row already exists) and Stripe
     * subscription auto-billing renewals (no BSM Payment row — created here).
     */
    private void handleStripeInvoicePaymentSucceeded(com.stripe.model.Invoice stripeInvoice) {
        String paymentIntentId = stripeInvoice.getPaymentIntent();
        if (paymentIntentId == null) {
            log.info("invoice.payment_succeeded stripeInvoiceId={} — no paymentIntent, skipping", stripeInvoice.getId());
            return;
        }

        // Path A — BSM-initiated payment: row already exists, update it
        Optional<Payment> existing = paymentRepository.findByExternalPaymentId(paymentIntentId);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            payment.markSucceeded(paymentIntentId);
            paymentRepository.save(payment);
            markInvoicePaid(payment);
            log.info("invoice.payment_succeeded (BSM-initiated) stripeInvoiceId={} bsmInvoiceId={}",
                stripeInvoice.getId(), payment.getInvoiceId());
            return;
        }

        // Path B — Stripe subscription auto-billing renewal: create row now
        String stripeSubId = stripeInvoice.getSubscription();
        if (stripeSubId == null) {
            log.warn("invoice.payment_succeeded stripeInvoiceId={} — no matching BSM payment and no subscriptionId on Stripe invoice",
                stripeInvoice.getId());
            return;
        }

        long amountPaid = stripeInvoice.getAmountPaid() != null ? stripeInvoice.getAmountPaid() : 0L;
        String currency = stripeInvoice.getCurrency();
        reconcileAutoRenewalSuccess(stripeInvoice.getId(), paymentIntentId, stripeSubId, amountPaid, currency);
    }

    /**
     * Handles Stripe subscription auto-billing failure. Creates a FAILED Payment record
     * and starts the BSM dunning process.
     *
     * <p>Previously this was a stub (log only). Now fully implemented for the
     * Stripe subscription auto-billing path.</p>
     */
    private void handleStripeInvoicePaymentFailed(com.stripe.model.Invoice stripeInvoice) {
        String stripeSubId = stripeInvoice.getSubscription();
        if (stripeSubId == null) {
            log.warn("invoice.payment_failed stripeInvoiceId={} — no subscription ID on invoice, cannot process",
                stripeInvoice.getId());
            return;
        }

        String paymentIntentId = stripeInvoice.getPaymentIntent();
        long amountDue = stripeInvoice.getAmountDue() != null ? stripeInvoice.getAmountDue() : 0L;
        String currency = stripeInvoice.getCurrency();
        reconcileAutoRenewalFailure(stripeInvoice.getId(), paymentIntentId, stripeSubId, amountDue, currency);
    }

    /**
     * Core reconciliation logic for a successful Stripe auto-billing renewal.
     * Package-private for testability without Stripe SDK objects.
     *
     * @param stripeInvoiceId  Stripe invoice ID (for logging)
     * @param paymentIntentId  Stripe PaymentIntent ID (used as externalPaymentId)
     * @param stripeSubId      Stripe Subscription ID (links to BSM subscription)
     * @param amountPaid       amount in minor units (e.g. pence)
     * @param currency         ISO 4217 currency code
     */
    void reconcileAutoRenewalSuccess(String stripeInvoiceId, String paymentIntentId,
                                     String stripeSubId, long amountPaid, String currency) {
        Optional<Subscription> subOpt = subscriptionRepository.findByExternalSubscriptionId(stripeSubId);
        if (subOpt.isEmpty()) {
            log.warn("invoice.payment_succeeded (auto) stripeInvoiceId={} — no BSM subscription for stripeSubId={}",
                stripeInvoiceId, stripeSubId);
            return;
        }
        Subscription subscription = subOpt.get();

        // Most-recent OPEN invoice for this subscription
        Optional<PlatformInvoice> openInvoice = invoiceRepository.findBySubscriptionId(subscription.getId())
            .stream()
            .filter(inv -> inv.getStatus() == InvoiceStatus.OPEN)
            .max(Comparator.comparing(PlatformInvoice::getCreatedAt));

        if (openInvoice.isEmpty()) {
            log.warn("invoice.payment_succeeded (auto) stripeInvoiceId={} — no OPEN BSM invoice for subscriptionId={}",
                stripeInvoiceId, subscription.getId());
            return;
        }

        PlatformInvoice invoice = openInvoice.get();
        long effectiveAmount = amountPaid > 0 ? amountPaid : invoice.getAmountDue();
        String effectiveCurrency = currency != null ? currency : invoice.getCurrency();

        Payment payment = Payment.builder()
            .id(UUID.randomUUID())
            .tenantId(subscription.getTenantId())
            .invoiceId(invoice.getId())
            .paymentProvider(subscription.getPaymentProvider() != null
                ? subscription.getPaymentProvider() : PaymentProvider.STRIPE)
            .externalPaymentId(paymentIntentId)
            .status(PaymentStatus.SUCCEEDED)
            .amountMinor(effectiveAmount)
            .currency(effectiveCurrency)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        try {
            paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            log.warn("invoice.payment_succeeded (auto) stripeInvoiceId={} — payment already recorded, idempotent skip",
                stripeInvoiceId);
            return;
        }

        markInvoicePaid(payment);
        log.info("invoice.payment_succeeded (auto-renewal) stripeInvoiceId={} bsmInvoiceId={} subscriptionId={}",
            stripeInvoiceId, invoice.getId(), subscription.getId());
    }

    /**
     * Core reconciliation logic for a failed Stripe auto-billing renewal.
     * Package-private for testability without Stripe SDK objects.
     *
     * @param stripeInvoiceId  Stripe invoice ID (for logging and fallback externalPaymentId)
     * @param paymentIntentId  Stripe PaymentIntent ID (may be null for zero-amount invoices)
     * @param stripeSubId      Stripe Subscription ID (links to BSM subscription)
     * @param amountDue        amount in minor units
     * @param currency         ISO 4217 currency code
     */
    void reconcileAutoRenewalFailure(String stripeInvoiceId, String paymentIntentId,
                                     String stripeSubId, long amountDue, String currency) {
        Optional<Subscription> subOpt = subscriptionRepository.findByExternalSubscriptionId(stripeSubId);
        if (subOpt.isEmpty()) {
            log.warn("invoice.payment_failed (auto) stripeInvoiceId={} — no BSM subscription for stripeSubId={}",
                stripeInvoiceId, stripeSubId);
            return;
        }
        Subscription subscription = subOpt.get();

        // Idempotency: if payment already recorded for this PaymentIntent, ensure dunning fires
        if (paymentIntentId != null) {
            Optional<Payment> existing = paymentRepository.findByExternalPaymentId(paymentIntentId);
            if (existing.isPresent()) {
                Payment p = existing.get();
                if (p.getStatus() != PaymentStatus.FAILED) {
                    p.markFailed("Stripe invoice payment failed: " + stripeInvoiceId);
                    paymentRepository.save(p);
                }
                triggerDunning(p);
                log.info("invoice.payment_failed (auto) stripeInvoiceId={} — payment already recorded, ensuring dunning",
                    stripeInvoiceId);
                return;
            }
        }

        // Most-recent OPEN invoice for this subscription
        Optional<PlatformInvoice> openInvoice = invoiceRepository.findBySubscriptionId(subscription.getId())
            .stream()
            .filter(inv -> inv.getStatus() == InvoiceStatus.OPEN)
            .max(Comparator.comparing(PlatformInvoice::getCreatedAt));

        if (openInvoice.isEmpty()) {
            log.warn("invoice.payment_failed (auto) stripeInvoiceId={} — no OPEN BSM invoice for subscriptionId={}",
                stripeInvoiceId, subscription.getId());
            return;
        }

        PlatformInvoice invoice = openInvoice.get();

        // Guard: if invoice already PAID (race with another success event), skip dunning
        if (invoice.isPaid()) {
            log.info("invoice.payment_failed (auto) stripeInvoiceId={} — invoice already PAID, skipping dunning",
                stripeInvoiceId);
            return;
        }

        long effectiveAmount = amountDue > 0 ? amountDue : invoice.getAmountDue();
        String effectiveCurrency = currency != null ? currency : invoice.getCurrency();
        // Fallback externalPaymentId when Stripe provides no PaymentIntent (e.g. zero-amount invoices)
        String externalId = paymentIntentId != null ? paymentIntentId : ("stripe-inv-" + stripeInvoiceId);

        Payment payment = Payment.builder()
            .id(UUID.randomUUID())
            .tenantId(subscription.getTenantId())
            .invoiceId(invoice.getId())
            .paymentProvider(subscription.getPaymentProvider() != null
                ? subscription.getPaymentProvider() : PaymentProvider.STRIPE)
            .externalPaymentId(externalId)
            .status(PaymentStatus.FAILED)
            .failureReason("Stripe subscription auto-billing failed")
            .amountMinor(effectiveAmount)
            .currency(effectiveCurrency)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        try {
            paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            log.warn("invoice.payment_failed (auto) stripeInvoiceId={} — payment already recorded, ensuring dunning",
                stripeInvoiceId);
            paymentRepository.findByExternalPaymentId(externalId).ifPresent(this::triggerDunning);
            return;
        }

        createLedgerEntry(payment, LedgerEntryType.ADJUSTMENT, 0L,
            "Stripe subscription auto-billing failed — stripeInvoice=" + stripeInvoiceId);

        log.info("invoice.payment_failed (auto-renewal) stripeInvoiceId={} bsmInvoiceId={} subscriptionId={}",
            stripeInvoiceId, invoice.getId(), subscription.getId());

        triggerDunning(payment);
    }

    // ── Stripe subscription lifecycle reconciliation ──────────────────────────

    /**
     * Reconciles a Stripe subscription deletion.  Called when Stripe fires
     * {@code customer.subscription.deleted} — e.g. a support agent cancels the
     * subscription directly in the Stripe dashboard, bypassing the BSM API.
     *
     * <p>Package-private for unit-test access without Stripe SDK objects.</p>
     */
    void reconcileSubscriptionDeleted(String externalSubscriptionId) {
        var subOpt = subscriptionRepository.findByExternalSubscriptionId(externalSubscriptionId);
        if (subOpt.isEmpty()) {
            log.warn("customer.subscription.deleted: no BSM subscription for stripeSubId={} — nothing to cancel",
                externalSubscriptionId);
            return;
        }
        var sub = subOpt.get();
        if (sub.getStatus() == SubscriptionStatus.CANCELLED) {
            log.info("customer.subscription.deleted: subscriptionId={} already CANCELLED — idempotent skip", sub.getId());
            return;
        }

        // cancelAndClearDunning() atomically sets status=CANCELLED and clears all dunning
        // fields (dunningStatus, dunningStartedAt, dunningNextActionAt) in a single save.
        // Using plain cancel() left dunningStatus set, which caused DunningScheduler to
        // continue retrying payments and — on success — unconditionally reactivate the
        // subscription to ACTIVE even though it was already cancelled.
        sub.cancelAndClearDunning(Instant.now());
        subscriptionRepository.save(sub);

        subscriptionHistoryRepository.save(SubscriptionHistory.builder()
            .id(UUID.randomUUID())
            .subscriptionId(sub.getId())
            .tenantId(sub.getTenantId())
            .action(SubscriptionHistoryAction.SUBSCRIPTION_CANCELLED)
            .reason("Subscription deleted in Stripe dashboard — reconciled via webhook")
            .performedBy("STRIPE_WEBHOOK")
            .actorId(UUID.nameUUIDFromBytes("STRIPE_WEBHOOK".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .actorType(ActorType.SYSTEM)
            .occurredAt(Instant.now())
            .build());

        try {
            subscriptionEventPublisher.publishCanceled(sub);
        } catch (Exception e) {
            log.warn("customer.subscription.deleted: outbox publish failed subscriptionId={}: {}", sub.getId(), e.getMessage());
        }

        log.info("customer.subscription.deleted: cancelled BSM subscriptionId={} tenantId={} via Stripe webhook",
            sub.getId(), sub.getTenantId());
    }

    /**
     * Reconciles a Stripe subscription update.  The only case that requires BSM action
     * is when Stripe transitions the subscription to {@code canceled} status (e.g. after
     * exhausting all dunning retries on the Stripe side, or a manual dashboard cancel).
     *
     * <p>Package-private for unit-test access.</p>
     */
    void reconcileSubscriptionUpdated(String externalSubscriptionId, String stripeStatus) {
        if ("canceled".equals(stripeStatus)) {
            // Stripe has cancelled the subscription — ensure BSM is in sync
            reconcileSubscriptionDeleted(externalSubscriptionId);
        } else {
            log.info("customer.subscription.updated: stripeSubId={} stripeStatus={} — no BSM action required",
                externalSubscriptionId, stripeStatus);
        }
    }

    /**
     * Reconciles a Stripe charge refund issued outside BSM (e.g. via Stripe dashboard).
     * Marks the BSM payment as REFUNDED and writes a REFUND ledger entry so the
     * revenue ledger remains accurate.
     *
     * <p>Package-private for unit-test access.</p>
     */
    void reconcileChargeRefunded(String chargeId, String paymentIntentId, long refundedAmount, String currency) {
        // Prefer lookup by payment intent (most common case — PI is stored as externalPaymentId)
        var paymentOpt = paymentIntentId != null
            ? paymentRepository.findByExternalPaymentId(paymentIntentId)
            : Optional.<Payment>empty();

        // Fall back to charge ID lookup (checkout-session flows that store charge ID)
        if (paymentOpt.isEmpty() && chargeId != null) {
            paymentOpt = paymentRepository.findByExternalChargeId(chargeId);
        }

        if (paymentOpt.isEmpty()) {
            log.warn("charge.refunded: no BSM payment for chargeId={} paymentIntentId={} — nothing to refund",
                chargeId, paymentIntentId);
            return;
        }

        var payment = paymentOpt.get();
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("charge.refunded: paymentId={} already REFUNDED — idempotent skip", payment.getId());
            return;
        }

        payment.markRefunded();
        paymentRepository.save(payment);

        long effectiveAmount = refundedAmount > 0 ? refundedAmount : payment.getAmountMinor();
        String effectiveCurrency = (currency != null && !currency.isBlank()) ? currency : payment.getCurrency();
        createLedgerEntry(
            payment, LedgerEntryType.REFUND, effectiveAmount,
            "Charge refunded via Stripe dashboard chargeId=" + chargeId
        );

        // A full refund must also update the invoice status to REFUNDED so dashboards and
        // revenue reports reflect the returned money.  Without this the invoice stays PAID
        // even though the payment was reversed, causing revenue to be over-reported.
        if (payment.getInvoiceId() != null) {
            try {
                var invoice = invoiceRepository.findById(payment.getInvoiceId()).orElse(null);
                if (invoice != null && invoice.getStatus() == com.company.bsmsvc.domain.enums.InvoiceStatus.PAID
                        && effectiveAmount >= invoice.getAmountPaid()) {
                    invoiceService.markRefunded(payment.getInvoiceId(), null);
                    log.info("charge.refunded: invoice marked REFUNDED invoiceId={}", payment.getInvoiceId());
                }
            } catch (Exception e) {
                log.warn("charge.refunded: could not mark invoice REFUNDED invoiceId={}: {}",
                    payment.getInvoiceId(), e.getMessage());
            }
        }

        log.info("charge.refunded: BSM paymentId={} marked REFUNDED chargeId={} amount={}{}",
            payment.getId(), chargeId, effectiveAmount, effectiveCurrency);
    }

    // ── Razorpay event routing ────────────────────────────────────────────────

    private void handleRazorpayEvent(String eventType, org.json.JSONObject body) {
        switch (eventType) {
            case "payment.captured" -> {
                org.json.JSONObject entity = extractRazorpayEntity(body, "payment");
                if (entity != null) {
                    String orderId = entity.optString("order_id");
                    String paymentId = entity.optString("id");
                    paymentRepository.findByExternalPaymentId(orderId).ifPresent(p -> {
                        p.markSucceeded(paymentId);
                        paymentRepository.save(p);
                        markInvoicePaid(p);
                        log.info("Razorpay payment.captured orderId={} paymentId={} invoiceId={}", orderId, paymentId, p.getInvoiceId());
                    });
                }
            }
            case "payment.failed" -> {
                org.json.JSONObject entity = extractRazorpayEntity(body, "payment");
                if (entity != null) {
                    String orderId = entity.optString("order_id");
                    String errorDesc = entity.optString("error_description", "Unknown");
                    paymentRepository.findByExternalPaymentId(orderId).ifPresent(p -> {
                        p.markFailed(errorDesc);
                        paymentRepository.save(p);
                        createLedgerEntry(p, LedgerEntryType.ADJUSTMENT, 0, "Razorpay payment failed: " + errorDesc);
                        triggerDunning(p);
                    });
                }
            }
            case "refund.created" -> log.info("Razorpay refund.created received");
            default -> log.debug("Razorpay event ignored: type={}", eventType);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void triggerDunning(Payment payment) {
        try {
            if (payment.getInvoiceId() != null) {
                var invoice = invoiceRepository.findById(payment.getInvoiceId()).orElse(null);
                if (invoice == null || invoice.getSubscriptionId() == null) return;
                // Guard: never start dunning for an already-paid invoice.
                if (invoice.isPaid()) {
                    log.info("triggerDunning: invoice already PAID — skipping dunning invoiceId={} paymentId={}",
                        invoice.getId(), payment.getId());
                    return;
                }
                dunningService.startDunning(invoice.getSubscriptionId(), payment.getInvoiceId());
            }
        } catch (Exception e) {
            log.warn("triggerDunning: failed to start dunning paymentId={}: {}", payment.getId(), e.getMessage());
        }
    }

    private Payment resolvePayment(String externalId, UUID invoiceId, String context) {
        Optional<Payment> byExternal = paymentRepository.findByExternalPaymentId(externalId);
        if (byExternal.isPresent()) return byExternal.get();

        if (invoiceId != null) {
            var payments = paymentRepository.findByInvoiceId(invoiceId);
            var pending = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .findFirst();
            if (pending.isPresent()) {
                log.info("{} resolved payment via invoice metadata invoiceId={}", context, invoiceId);
                return pending.get();
            }
        }

        log.warn("{} — no payment record found for externalId={} invoiceId={}", context, externalId, invoiceId);
        return null;
    }

    private UUID extractInvoiceId(Map<String, String> metadata) {
        if (metadata == null) return null;
        String raw = metadata.get("invoiceId");
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid invoiceId in metadata: {}", raw);
            return null;
        }
    }

    private org.json.JSONObject extractRazorpayEntity(org.json.JSONObject body, String entityKey) {
        try {
            return body.getJSONObject("payload").getJSONObject(entityKey).getJSONObject("entity");
        } catch (Exception e) {
            return null;
        }
    }

    private void markInvoicePaid(Payment payment) {
        // Step 1: mark the invoice PAID.  applyPayment() writes the INVOICE_PAID ledger entry
        // automatically via InvoiceMarkedPaidEvent in PlatformInvoiceRepositoryAdapter.save().
        // Do NOT write a second explicit ledger entry here — doing so produces two INVOICE_PAID
        // rows per payment, making revenue reconciliation impossible.
        try {
            invoiceService.applyPayment(payment.getInvoiceId(), payment.getAmountMinor(), null);
            log.info("Invoice marked PAID invoiceId={}", payment.getInvoiceId());
        } catch (Exception ex) {
            log.warn("Failed to mark invoice PAID invoiceId={} (may already be PAID): {}",
                payment.getInvoiceId(), ex.getMessage());
        }

        // Step 2: recover subscription from dunning if payment arrived outside the dunning retry flow
        try {
            var invoice = invoiceRepository.findById(payment.getInvoiceId()).orElse(null);
            if (invoice != null && invoice.getSubscriptionId() != null) {
                dunningService.recoveryPaymentReceived(invoice.getSubscriptionId(), payment.getInvoiceId());
            }
        } catch (Exception e) {
            log.warn("markInvoicePaid: dunning recovery check failed paymentId={}: {}", payment.getId(), e.getMessage());
        }
    }

    private void createLedgerEntry(Payment payment, LedgerEntryType type, long amount, String description) {
        ledgerRepository.save(BillingLedgerEntry.builder()
            .id(UUID.randomUUID())
            .tenantId(payment.getTenantId())
            .invoiceId(payment.getInvoiceId())
            .entryType(type)
            .amountMinor(amount)
            .currency(payment.getCurrency())
            .description(description)
            .createdAt(Instant.now())
            .build());
    }

    private boolean isDuplicate(PaymentProvider provider, String externalEventId) {
        return webhookEventRepository.findByProviderAndExternalEventId(provider, externalEventId).isPresent();
    }

    private WebhookEvent storeWebhookEvent(PaymentProvider provider, String externalEventId, String eventType, String payload) {
        return webhookEventRepository.save(WebhookEvent.builder()
            .id(UUID.randomUUID()).provider(provider).externalEventId(externalEventId)
            .eventType(eventType).payload(payload).status(WebhookEventStatus.RECEIVED)
            .receivedAt(Instant.now()).build());
    }

    private void markProcessed(WebhookEvent event) {
        webhookEventRepository.save(event.toBuilder().status(WebhookEventStatus.PROCESSED).processedAt(Instant.now()).build());
    }

    private void markFailed(WebhookEvent event, String reason) {
        webhookEventRepository.save(event.toBuilder().status(WebhookEventStatus.FAILED).failureReason(reason).processedAt(Instant.now()).build());
    }

    private void verifyRazorpaySignature(String payload, String signature) {
        try {
            String secret = webhookProperties.razorpaySecret();
            if (secret == null || secret.isBlank()) {
                throw new WebhookVerificationException(
                    "Razorpay webhook secret is not configured — set RAZORPAY_WEBHOOK_SECRET");
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            if (!hex.toString().equals(signature)) {
                throw new WebhookVerificationException("Razorpay webhook signature mismatch");
            }
        } catch (WebhookVerificationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebhookVerificationException("Razorpay signature verification error", e);
        }
    }
}
