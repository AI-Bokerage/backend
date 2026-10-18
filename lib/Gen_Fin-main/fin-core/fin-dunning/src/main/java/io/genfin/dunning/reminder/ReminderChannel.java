package io.genfin.dunning.reminder;

/**
 * The kind of channel a {@link Reminder} is destined for (what an application might call email,
 * SMS, push, or in-app). Not a closed enum - fin-dunning defines no delivery channels of its own;
 * an application registers whatever channels its own notification infrastructure supports, the same
 * extensible-taxonomy pattern as {@code io.genfin.dunning.obligation.ObligationType}. fin-dunning
 * never sends anything on any channel - this is a label the consuming application interprets.
 */
public interface ReminderChannel {

  String code();
}
