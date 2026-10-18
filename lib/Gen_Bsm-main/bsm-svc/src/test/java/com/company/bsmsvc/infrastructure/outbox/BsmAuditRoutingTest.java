package com.company.bsmsvc.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.cpms.common.messaging.AuditMessagingTopology;
import com.company.bsmsvc.messaging.BsmMessagingRouting;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every bsm-svc event in this phase's scope must route to {@code cpms.audit} — verified
 * individually per event type string (not spot-checked), per Phase 4's own required-tests list.
 */
@DisplayName("BsmAuditEventRouter — every in-scope event routes to cpms.audit")
class BsmAuditRoutingTest {

    private final BsmAuditEventRouter router = new BsmAuditEventRouter();

    static Stream<String> allInScopeEventTypes() {
        return Stream.of(
            // subscription (dual-publish rows)
            BsmMessagingRouting.BSM_SUBSCRIPTION_CREATED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_CHANGED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_CANCELED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_EXPIRED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_RENEWED,
            // invoice
            "bsm.invoice.created",
            // dunning
            "bsm.dunning.started", "bsm.dunning.retry", "bsm.dunning.recovered",
            "bsm.dunning.suspended", "bsm.dunning.cancelled",
            // payment
            "payment.created", "payment.captured", "payment.failed",
            // refund
            "refund.created", "refund.completed", "refund.failed",
            // credit note
            "credit_note.created", "credit_note.applied", "credit_note.voided"
        );
    }

    @ParameterizedTest(name = "{0} routes to cpms.audit")
    @MethodSource("allInScopeEventTypes")
    void everyInScopeEventRoutesToAudit(String eventType) {
        var routing = router.resolve(eventType);
        assertThat(routing).as("routing for %s", eventType).isPresent();
        assertThat(routing.get().exchanges())
            .as("exchanges for %s", eventType)
            .containsExactly(AuditMessagingTopology.AUDIT_EXCHANGE);
        // No event in this phase is platform-tier or Both — cpms.audit is the ONLY audit exchange.
        assertThat(routing.get().exchanges()).doesNotContain(AuditMessagingTopology.PLATFORM_AUDIT_EXCHANGE);
    }

    @Test
    @DisplayName("subscription events also keep publishing to cpms.events (alsoDefault=true)")
    void subscriptionEvents_alsoDefaultTrue() {
        List<String> subscriptionEvents = List.of(
            BsmMessagingRouting.BSM_SUBSCRIPTION_CREATED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_CHANGED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_CANCELED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_EXPIRED,
            BsmMessagingRouting.BSM_SUBSCRIPTION_RENEWED
        );
        for (String eventType : subscriptionEvents) {
            assertThat(router.resolve(eventType).orElseThrow().alsoDefault())
                .as("alsoDefault for %s", eventType)
                .isTrue();
        }
    }

    @Test
    @DisplayName("new audit-only legs (invoice/dunning/payment/refund/credit_note) do NOT also publish to cpms.events")
    void auditOnlyEvents_alsoDefaultFalse() {
        List<String> auditOnlyEvents = List.of(
            "bsm.invoice.created",
            "bsm.dunning.started", "bsm.dunning.retry", "bsm.dunning.recovered",
            "bsm.dunning.suspended", "bsm.dunning.cancelled",
            "payment.created", "payment.captured", "payment.failed",
            "refund.created", "refund.completed", "refund.failed",
            "credit_note.created", "credit_note.applied", "credit_note.voided"
        );
        for (String eventType : auditOnlyEvents) {
            assertThat(router.resolve(eventType).orElseThrow().alsoDefault())
                .as("alsoDefault for %s", eventType)
                .isFalse();
        }
    }

    @Test
    @DisplayName("unknown event type resolves to empty — no crash, no route")
    void unknownEventType_resolvesEmpty() {
        assertThat(router.resolve("totally.unknown.event")).isEmpty();
        assertThat(router.resolve(null)).isEmpty();
    }
}
