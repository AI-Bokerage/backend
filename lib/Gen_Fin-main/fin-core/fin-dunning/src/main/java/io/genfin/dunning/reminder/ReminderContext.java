package io.genfin.dunning.reminder;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Instant;
import java.util.Set;

/**
 * Per-invocation context a {@code ReminderStrategy} plans against: the instant planning is
 * evaluated as-of, and the reminder sequence numbers the application has already dispatched for
 * this obligation (so replanning never re-plans a reminder that has already gone out). fin-dunning
 * never tracks this itself - the application supplies it from its own dispatch history.
 */
public record ReminderContext(Instant asOf, Set<Integer> alreadySentSequenceNumbers)
    implements ValueObject {

  public ReminderContext {
    Validate.notNull(asOf, "asOf must not be null.");
    alreadySentSequenceNumbers = Set.copyOf(alreadySentSequenceNumbers);
  }

  public static ReminderContext of(Instant asOf, Set<Integer> alreadySentSequenceNumbers) {
    return new ReminderContext(asOf, alreadySentSequenceNumbers);
  }

  /** Context carrying no dispatch history - every reminder in the schedule is still pending. */
  public static ReminderContext asOf(Instant asOf) {
    return new ReminderContext(asOf, Set.of());
  }

  public boolean alreadySent(int sequenceNumber) {
    return alreadySentSequenceNumbers.contains(sequenceNumber);
  }
}
