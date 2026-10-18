package io.genfin.refund.attempt;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.refund.id.RefundAttemptId;
import java.util.Optional;

/**
 * Points a retried {@link RefundAttempt} back at the attempt it retries. {@link #initial()} marks
 * the first attempt in a sequence, which retries nothing.
 */
public record RetryReference(RefundAttemptId previousAttemptId) implements ValueObject {

  private static final RetryReference INITIAL = new RetryReference(null);

  public static RetryReference initial() {
    return INITIAL;
  }

  public static RetryReference retryOf(RefundAttemptId previousAttemptId) {
    return new RetryReference(
        Validate.notNull(previousAttemptId, "previousAttemptId must not be null."));
  }

  public Optional<RefundAttemptId> previousAttempt() {
    return Optional.ofNullable(previousAttemptId);
  }

  public boolean isRetry() {
    return previousAttemptId != null;
  }
}
