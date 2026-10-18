package io.genfin.dunning.rule;

/**
 * The kind of outcome a {@link CollectionDecision} recommends (what an application might call
 * skipping today's action, pausing the case, holding it for manual attention, or flagging it for
 * priority handling). Not a closed enum - fin-dunning defines no collection outcomes of its own; an
 * application registers whatever outcomes its own collection workflows support, the same
 * extensible-taxonomy pattern as {@code io.genfin.dunning.escalation.EscalationAction}. fin-dunning
 * never acts on this outcome itself - it only labels which one a rule found to apply.
 */
public interface CollectionOutcome {

  String code();
}
