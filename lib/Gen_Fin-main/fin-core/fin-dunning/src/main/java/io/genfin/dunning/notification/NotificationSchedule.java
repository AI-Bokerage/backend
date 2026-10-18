package io.genfin.dunning.notification;

import io.genfin.api.domain.ValueObject;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The full, explicit set of {@link NotificationPreference}s an application wants applied when
 * resolving a {@link NotificationOccurrence} into a {@link NotificationStep} - which channel/
 * template to use instead of an occurrence's own fallback. Resolved from an application's own
 * dunning policy; fin-dunning ships none of its own.
 */
public record NotificationSchedule(List<NotificationPreference> preferences)
    implements ValueObject {

  public NotificationSchedule {
    preferences = List.copyOf(preferences);
  }

  public static NotificationSchedule of(List<NotificationPreference> preferences) {
    return new NotificationSchedule(preferences);
  }

  public static NotificationSchedule empty() {
    return new NotificationSchedule(List.of());
  }

  /**
   * The most specific matching preference for an occurrence - one keyed to its exact sequence
   * number if present, otherwise a blanket preference for its source, otherwise empty (meaning the
   * occurrence's own fallback channel/template applies unchanged).
   */
  public Optional<NotificationPreference> preferenceFor(
      NotificationSource source, int sequenceNumber) {
    return preferences.stream()
        .filter(preference -> preference.matches(source, sequenceNumber))
        .max(Comparator.comparing(NotificationPreference::isSpecific));
  }
}
