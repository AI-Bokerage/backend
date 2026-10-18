package io.genfin.dunning.failure;

/**
 * The kind of handling a {@link FailureClassification} recommends for a payment-not-received event
 * (what an application might call retrying, escalating immediately, writing off, or routing to
 * manual review). Not a closed enum - fin-dunning defines no dispositions of its own; an
 * application registers whatever its own operational workflows support, the same
 * extensible-taxonomy pattern as {@code io.genfin.dunning.escalation.EscalationAction}. fin-dunning
 * never performs this disposition - it only labels which one applies, leaving the actual retry
 * scheduling to the Retry Engine and the actual escalation to the Escalation Engine.
 */
public interface FailureDisposition {

  String code();
}
