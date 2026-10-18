package io.genfin.dunning.retry;

import io.genfin.api.domain.Entity;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.id.RetryId;
import io.genfin.payment.failure.FailureReason;
import java.time.Instant;
import java.util.Optional;

/**
 * One executed retry attempt against an obligation, as reported back by the consuming application
 * after it actually performed the attempt fin-dunning only planned. Obligation → Attempt 1 →
 * Attempt 2 → ... - never overwritten, always appended to a {@link RetryHistory}. {@link
 * FailureReason} is reused as-is from fin-payment: a fully generic failure description, not
 * provider- or CPMS-specific.
 */
public final class RetryExecution extends Entity<RetryId> {

  private final int attemptNumber;
  private final Instant scheduledAt;
  private final Instant occurredAt;
  private final RetryResult result;
  private final FailureReason failureReason;

  public RetryExecution(
      RetryId id,
      int attemptNumber,
      Instant scheduledAt,
      Instant occurredAt,
      RetryResult result,
      FailureReason failureReason) {
    super(id);
    this.attemptNumber = Validate.positive(attemptNumber, "attemptNumber must be positive.");
    this.scheduledAt = Validate.notNull(scheduledAt, "scheduledAt must not be null.");
    this.occurredAt = Validate.notNull(occurredAt, "occurredAt must not be null.");
    this.result = Validate.notNull(result, "result must not be null.");
    this.failureReason = failureReason;
  }

  public int attemptNumber() {
    return attemptNumber;
  }

  public Instant scheduledAt() {
    return scheduledAt;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public RetryResult result() {
    return result;
  }

  public Optional<FailureReason> failureReason() {
    return Optional.ofNullable(failureReason);
  }

  public boolean succeeded() {
    return result == RetryResult.SUCCEEDED;
  }
}
