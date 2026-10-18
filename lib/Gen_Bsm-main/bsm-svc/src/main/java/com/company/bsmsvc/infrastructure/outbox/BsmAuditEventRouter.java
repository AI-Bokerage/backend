package com.company.bsmsvc.infrastructure.outbox;

import io.cpms.common.messaging.AuditMessagingTopology;
import com.company.bsmsvc.messaging.BsmMessagingRouting;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Pure classification: the wire event-type string carried as {@code bsm_outbox_events.routing_key}
 * (which, for every row this router recognizes, is identical to the dotted wire string aud-svc's
 * cross-service audit pipeline expects) resolves to whether/where this outbox row should
 * additionally be published for audit purposes. No I/O, no Rabbit dependency — independently
 * unit-testable, mirroring adm-svc's {@code AdmAuditEventRouter} and tnt-svc's
 * {@code AuditEventRouter}.
 *
 * <p><b>Everything in this phase is tenant-tier only</b> ({@code cpms.audit}) — confirmed via
 * {@code io.cpms.aud.domain.enums.PlatformEventCatalog} (aud-svc's live catalog) having zero
 * billing-domain entries, and {@code docs/audit/02-event-catalog.md} classifying every bsm-svc
 * event family {@code AUDIT-SVC} (never {@code Both}/platform-tier). So {@link Routing#exchanges()}
 * below is always {@code List.of(AuditMessagingTopology.AUDIT_EXCHANGE)} — no platform-tier
 * branch exists to wire.
 *
 * <p><b>{@code alsoDefault} distinguishes two categories of outbox row:</b>
 * <ul>
 *   <li><b>Subscription events</b> ({@code bsm.subscription.*}) — the SAME outbox row that already
 *       publishes to {@code cpms.events} (today's default, five real business publishes) — here
 *       {@code alsoDefault=true} so {@link BsmOutboxPublisher} keeps sending to
 *       {@code CPMS_EVENTS_EXCHANGE} completely unchanged AND additionally, independently,
 *       publishes the same payload to {@code cpms.audit}. This is the "add never replace" dual
 *       publish.</li>
 *   <li><b>Every other event routed here</b> (invoice.created's NEW audit leg, all 5 dunning audit
 *       legs, and the real payment/refund/credit-note lifecycle events) — these outbox rows exist
 *       ONLY for audit purposes (their business-facing publish, where one exists at all, happens
 *       through a completely different, untouched mechanism — direct RabbitTemplate for invoice/
 *       dunning, or no messaging at all for payment/refund/credit-note). {@code alsoDefault=false}
 *       so the publisher sends ONLY to {@code cpms.audit} — never inventing new traffic on
 *       {@code cpms.events} for events that never had a business-exchange presence there.</li>
 * </ul>
 *
 * <p>Any routing key not present in {@link #ROUTES} resolves to {@link Optional#empty()}, in which
 * case {@link BsmOutboxPublisher} falls through to its original, single-exchange
 * {@code CPMS_EVENTS_EXCHANGE} default — unchanged behavior for anything not in scope of this
 * phase.
 */
@Component
public class BsmAuditEventRouter {

    /**
     * A resolved routing decision. {@code exchanges} are the audit-tier exchange(s) to publish to
     * (always just {@code cpms.audit} in this phase); {@code alsoDefault} says whether
     * {@code CPMS_EVENTS_EXCHANGE} must ALSO still be published to (true only for the five
     * pre-existing subscription events, to preserve their current business-facing behavior
     * byte-for-byte).
     */
    public record Routing(List<String> exchanges, boolean alsoDefault) {
    }

    private static final List<String> AUDIT_ONLY = List.of(AuditMessagingTopology.AUDIT_EXCHANGE);

    private static final Map<String, Routing> ROUTES = Map.ofEntries(
            // ── Subscription lifecycle — existing cpms.events business publish PRESERVED,
            // ── audit leg is a new, additional, independent publish. ─────────────────────────
            Map.entry(BsmMessagingRouting.BSM_SUBSCRIPTION_CREATED, new Routing(AUDIT_ONLY, true)),
            Map.entry(BsmMessagingRouting.BSM_SUBSCRIPTION_CHANGED, new Routing(AUDIT_ONLY, true)),
            Map.entry(BsmMessagingRouting.BSM_SUBSCRIPTION_CANCELED, new Routing(AUDIT_ONLY, true)),
            Map.entry(BsmMessagingRouting.BSM_SUBSCRIPTION_EXPIRED, new Routing(AUDIT_ONLY, true)),
            Map.entry(BsmMessagingRouting.BSM_SUBSCRIPTION_RENEWED, new Routing(AUDIT_ONLY, true)),

            // ── Invoice — audit-only leg. Business publish (direct RabbitTemplate → cpms.events)
            // ── is untouched (RabbitInvoiceEventPublisher). This is a SEPARATE new outbox row. ──
            Map.entry("bsm.invoice.created", new Routing(AUDIT_ONLY, false)),
            // Added for producer-conformance pass (docs/audit/17): InvoiceServiceImpl#applyPayment/
            // voidInvoice/markRefunded were real, live business operations (REST endpoints,
            // gated by billing.write/billing.admin) with zero audit publish before this.
            Map.entry("bsm.invoice.paid", new Routing(AUDIT_ONLY, false)),
            Map.entry("bsm.invoice.voided", new Routing(AUDIT_ONLY, false)),
            Map.entry("bsm.invoice.refunded", new Routing(AUDIT_ONLY, false)),

            // ── Tenant billing profile — audit-only legs. TenantBillingProfileRepositoryAdapter
            // ── already registers these 4 domain events on every write; previously only
            // ── log.debug()'d (TenantBillingCurrencyChangedEvent wasn't even logged). No actor
            // ── available at the repository layer — SYSTEM, not fabricated (see adapter). ──────
            Map.entry("bsm.billing_profile.created", new Routing(AUDIT_ONLY, false)),
            Map.entry("bsm.billing_profile.provider_changed", new Routing(AUDIT_ONLY, false)),
            Map.entry("bsm.billing_profile.currency_changed", new Routing(AUDIT_ONLY, false)),
            Map.entry("bsm.billing_profile.customer_created", new Routing(AUDIT_ONLY, false)),

            // ── Dunning — audit-only legs. Business publish (direct RabbitTemplate → cpms.events)
            // ── is untouched (RabbitDunningEventPublisher). Separate new outbox rows. ───────────
            Map.entry("bsm.dunning.started", new Routing(AUDIT_ONLY, false)),
            Map.entry("bsm.dunning.retry", new Routing(AUDIT_ONLY, false)),
            Map.entry("bsm.dunning.recovered", new Routing(AUDIT_ONLY, false)),
            Map.entry("bsm.dunning.suspended", new Routing(AUDIT_ONLY, false)),
            Map.entry("bsm.dunning.cancelled", new Routing(AUDIT_ONLY, false)),

            // ── Payment — audit-only legs. No pre-existing business-exchange presence at all for
            // ── these; only created/captured/failed have a real call site (see
            // ── PaymentServiceImpl#persistPaymentRecord, PaymentReconciliationServiceImpl
            // ── #reconcilePayment). "authorized"/"reversed" have no corresponding real code path
            // ── today and are NOT wired — reported, not fabricated. ─────────────────────────────
            Map.entry("payment.created", new Routing(AUDIT_ONLY, false)),
            Map.entry("payment.captured", new Routing(AUDIT_ONLY, false)),
            Map.entry("payment.failed", new Routing(AUDIT_ONLY, false)),

            // ── Refund — audit-only legs. All 3 requested lifecycle events have a real call site
            // ── (RefundServiceImpl#createRefund for created/failed/completed,
            // ── RefundRecoveryServiceImpl#recover for a second completed call site). ────────────
            Map.entry("refund.created", new Routing(AUDIT_ONLY, false)),
            Map.entry("refund.completed", new Routing(AUDIT_ONLY, false)),
            Map.entry("refund.failed", new Routing(AUDIT_ONLY, false)),

            // ── Credit note — audit-only legs. All 3 requested lifecycle events have a real call
            // ── site (CreditNoteServiceImpl#createCreditNote/#applyCreditNote/#voidCreditNote). ─
            Map.entry("credit_note.created", new Routing(AUDIT_ONLY, false)),
            Map.entry("credit_note.applied", new Routing(AUDIT_ONLY, false)),
            Map.entry("credit_note.voided", new Routing(AUDIT_ONLY, false))
    );

    /**
     * Resolves the routing decision for a bsm-svc outbox row's routing key. Empty for anything not
     * in the table above — {@link BsmOutboxPublisher} keeps its original single-exchange default
     * unchanged in that case (no crash, no-op from this router's perspective).
     */
    public Optional<Routing> resolve(String routingKey) {
        if (routingKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ROUTES.get(routingKey));
    }
}
