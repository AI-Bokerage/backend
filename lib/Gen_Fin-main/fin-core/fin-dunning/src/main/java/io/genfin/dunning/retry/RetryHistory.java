package io.genfin.dunning.retry;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Append-only, ordered history of {@link RetryExecution}s for a single obligation. Prior attempts
 * are never overwritten or removed - retrying only appends a new execution. Mirrors fin-refund's
 * {@code AttemptSequence} philosophy.
 */
public final class RetryHistory implements ValueObject {

  private static final RetryHistory EMPTY = new RetryHistory(List.of());

  private final List<RetryExecution> executions;

  private RetryHistory(List<RetryExecution> executions) {
    this.executions = List.copyOf(executions);
  }

  public static RetryHistory empty() {
    return EMPTY;
  }

  public static RetryHistory of(List<RetryExecution> executions) {
    return executions.isEmpty() ? EMPTY : new RetryHistory(executions);
  }

  public RetryHistory append(RetryExecution execution) {
    Validate.notNull(execution, "execution must not be null.");
    List<RetryExecution> updated = new ArrayList<>(executions);
    updated.add(execution);
    return new RetryHistory(updated);
  }

  public List<RetryExecution> all() {
    return executions;
  }

  public Optional<RetryExecution> latest() {
    return executions.isEmpty()
        ? Optional.empty()
        : Optional.of(executions.get(executions.size() - 1));
  }

  public int nextAttemptNumber() {
    return executions.size() + 1;
  }

  public int size() {
    return executions.size();
  }

  public boolean isEmpty() {
    return executions.isEmpty();
  }
}
