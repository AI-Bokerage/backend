package io.genfin.dunning.notification;

import io.genfin.api.validation.Validate;

/**
 * A small convenience set of common notification sources. {@code REMINDER} is the only source
 * fin-dunning itself currently produces (via {@code ReminderPlan}, adapted through {@link
 * NotificationOccurrences#fromReminderPlan}); {@code ESCALATION} is offered here so a future
 * escalation stage - and any application anticipating one - can reuse the same code rather than
 * inventing its own. fin-dunning ships no escalation logic yet.
 */
public record StandardNotificationSource(String code) implements NotificationSource {

  public StandardNotificationSource {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardNotificationSource of(String code) {
    return new StandardNotificationSource(code);
  }

  public static final StandardNotificationSource REMINDER = of("REMINDER");
  public static final StandardNotificationSource ESCALATION = of("ESCALATION");
}
