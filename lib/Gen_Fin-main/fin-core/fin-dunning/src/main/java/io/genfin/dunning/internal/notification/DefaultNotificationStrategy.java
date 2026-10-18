package io.genfin.dunning.internal.notification;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.notification.NotificationOccurrence;
import io.genfin.dunning.notification.NotificationPlan;
import io.genfin.dunning.notification.NotificationPreference;
import io.genfin.dunning.notification.NotificationSchedule;
import io.genfin.dunning.notification.NotificationStep;
import io.genfin.dunning.port.notification.NotificationStrategy;
import java.util.List;
import java.util.Optional;

/**
 * Resolves each {@link NotificationOccurrence} into a {@link NotificationStep}: a matching {@link
 * NotificationPreference} in the schedule wins, the occurrence's own fallback channel/template
 * applies otherwise. Never sends anything.
 */
public final class DefaultNotificationStrategy implements NotificationStrategy {

  @Override
  public NotificationPlan plan(
      List<NotificationOccurrence> occurrences, NotificationSchedule schedule) {
    Validate.notNull(occurrences, "occurrences must not be null.");
    Validate.notNull(schedule, "schedule must not be null.");

    List<NotificationStep> steps =
        occurrences.stream().map(occurrence -> resolve(occurrence, schedule)).toList();
    return NotificationPlan.of(steps);
  }

  private static NotificationStep resolve(
      NotificationOccurrence occurrence, NotificationSchedule schedule) {
    Optional<NotificationPreference> preference =
        schedule.preferenceFor(occurrence.source(), occurrence.sequenceNumber());

    return new NotificationStep(
        occurrence.sequenceNumber(),
        occurrence.source(),
        preference.map(NotificationPreference::channel).orElse(occurrence.fallbackChannel()),
        preference.map(NotificationPreference::template).orElse(occurrence.fallbackTemplate()),
        occurrence.window());
  }
}
