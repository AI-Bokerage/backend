package io.genfin.dunning.notification;

import io.genfin.api.domain.ValueObject;
import java.util.List;

/**
 * The full, ordered set of {@link NotificationStep}s a {@code NotificationStrategy} resolved across
 * every {@link NotificationOccurrence} handed to it - reminder-driven, escalation-driven, or any
 * future source alike. This is a planning-only layer distinct from {@code ReminderPlan}: it is the
 * single point where notifications from every dunning stage converge before dispatch, so an
 * application never has to fan out to each stage's plan type separately. Data only, never executed
 * by fin-dunning - the consuming application dispatches each planned step itself.
 */
public record NotificationPlan(List<NotificationStep> steps) implements ValueObject {

  public NotificationPlan {
    steps = List.copyOf(steps);
  }

  public static NotificationPlan of(List<NotificationStep> steps) {
    return new NotificationPlan(steps);
  }

  public static NotificationPlan empty() {
    return new NotificationPlan(List.of());
  }
}
