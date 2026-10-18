package io.genfin.dunning.failure;

/**
 * The broad kind a {@link FailureReason} falls into (what an application might call "temporary",
 * "permanent", "gateway", "customer", "fraud", or "business"). Not a closed enum - Gen-Fin defines
 * no business failure taxonomy of its own; every consuming application is free to implement this
 * interface for its own classification scheme, the same extensible-taxonomy pattern as {@code
 * io.genfin.dunning.escalation.EscalationAction} / {@code
 * io.genfin.dunning.obligation.ObligationType}.
 */
public interface FailureCategory {

  String code();
}
