package io.genfin.refund.attempt;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Append-only, ordered history of {@link RefundAttempt}s for a single refund. Prior attempts are
 * never overwritten or removed — retrying only appends a new attempt.
 */
public final class AttemptSequence implements ValueObject {

  private static final AttemptSequence EMPTY = new AttemptSequence(List.of());

  private final List<RefundAttempt> attempts;

  private AttemptSequence(List<RefundAttempt> attempts) {
    this.attempts = List.copyOf(attempts);
  }

  public static AttemptSequence empty() {
    return EMPTY;
  }

  public static AttemptSequence of(List<RefundAttempt> attempts) {
    return attempts.isEmpty() ? EMPTY : new AttemptSequence(attempts);
  }

  public AttemptSequence append(RefundAttempt attempt) {
    Validate.notNull(attempt, "attempt must not be null.");
    List<RefundAttempt> updated = new ArrayList<>(attempts);
    updated.add(attempt);
    return new AttemptSequence(updated);
  }

  public List<RefundAttempt> all() {
    return attempts;
  }

  public Optional<RefundAttempt> latest() {
    return attempts.isEmpty() ? Optional.empty() : Optional.of(attempts.get(attempts.size() - 1));
  }

  public int nextAttemptNumber() {
    return attempts.size();
  }

  public int size() {
    return attempts.size();
  }

  public boolean isEmpty() {
    return attempts.isEmpty();
  }
}
