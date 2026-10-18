package io.genfin.dunning.escalation;

/**
 * The kind of action an {@link EscalationDecision} recommends (what an application might call
 * notifying a manager, suspending a service, freezing an account, writing off the obligation, or
 * routing it to manual review). Not a closed enum - fin-dunning defines no escalation actions of
 * its own; an application registers whatever actions its own operational workflows support, the
 * same extensible-taxonomy pattern as {@code io.genfin.dunning.reminder.ReminderChannel}.
 * fin-dunning never performs this action - it only labels which one applies.
 */
public interface EscalationAction {

  String code();
}
