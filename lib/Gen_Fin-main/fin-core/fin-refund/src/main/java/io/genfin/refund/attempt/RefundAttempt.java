package io.genfin.refund.attempt;

import io.genfin.api.domain.Entity;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.payment.failure.FailureReason;
import io.genfin.refund.id.RefundAttemptId;
import io.genfin.refund.metadata.RefundMetadata;
import java.time.Instant;
import java.util.Optional;

/**
 * One execution attempt at fulfilling a {@code Refund}. {@code Refund} → Attempt 1 → Attempt 2 →
 * ... — never overwritten, always appended to an {@link AttemptSequence}. {@link FailureReason} is
 * reused as-is from fin-payment: it is a fully generic failure description, not CPMS- or
 * provider-specific.
 */
public final class RefundAttempt extends Entity<RefundAttemptId> {

  private final int attemptNumber;
  private final Instant occurredAt;
  private final Money amount;
  private final RefundAttemptResult result;
  private final FailureReason failureReason;
  private final RetryReference retryReference;
  private final String gatewayReference;
  private final RefundMetadata metadata;

  public RefundAttempt(
      RefundAttemptId id,
      int attemptNumber,
      Instant occurredAt,
      Money amount,
      RefundAttemptResult result,
      FailureReason failureReason,
      RetryReference retryReference,
      String gatewayReference,
      RefundMetadata metadata) {
    super(id);
    this.attemptNumber = Validate.nonNegative(attemptNumber, "attemptNumber must not be negative.");
    this.occurredAt = Validate.notNull(occurredAt, "occurredAt must not be null.");
    this.amount = Validate.notNull(amount, "amount must not be null.");
    this.result = Validate.notNull(result, "result must not be null.");
    this.failureReason = failureReason;
    this.retryReference = retryReference == null ? RetryReference.initial() : retryReference;
    this.gatewayReference = gatewayReference;
    this.metadata = metadata == null ? RefundMetadata.empty() : metadata;
  }

  public int attemptNumber() {
    return attemptNumber;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public Money amount() {
    return amount;
  }

  public RefundAttemptResult result() {
    return result;
  }

  public Optional<FailureReason> failureReason() {
    return Optional.ofNullable(failureReason);
  }

  public RetryReference retryReference() {
    return retryReference;
  }

  public Optional<String> gatewayReference() {
    return Optional.ofNullable(gatewayReference);
  }

  public RefundMetadata metadata() {
    return metadata;
  }

  public boolean succeeded() {
    return result == RefundAttemptResult.SUCCEEDED;
  }
}
