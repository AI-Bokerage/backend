package io.genfin.dunning.notification;

/**
 * What triggered a planned notification - a reminder occurrence, an escalation decision, or
 * whatever else an application's own dunning policy produces. Not a closed enum, same
 * extensible-taxonomy pattern as {@link io.genfin.dunning.reminder.ReminderChannel} and {@code
 * ObligationType}: fin-dunning ships only the source it itself currently produces ({@link
 * StandardNotificationSource#REMINDER}), an application (or a later fin-dunning escalation stage)
 * registers whatever other sources it needs.
 */
public interface NotificationSource {

  String code();
}
