package io.genfin.dunning.escalation;

/**
 * The severity rung an {@link EscalationDecision} escalates a {@code DunningCase} to (what an
 * application might call "Level 1", "Manager Review", or "Legal"). Not a closed enum - Gen-Fin
 * defines no business escalation levels of its own; every consuming application is free to
 * implement this interface for its own ladder, the same extensible-taxonomy pattern as {@code
 * io.genfin.dunning.obligation.ObligationType} / {@code io.genfin.ledger.account.AccountType}.
 */
public interface EscalationLevel {

  String code();
}
