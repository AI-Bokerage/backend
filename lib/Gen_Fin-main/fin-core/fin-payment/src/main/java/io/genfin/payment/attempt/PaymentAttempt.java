package io.genfin.payment.attempt;

import io.genfin.api.domain.Entity;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.payment.failure.FailureReason;
import io.genfin.payment.id.AttemptId;
import io.genfin.payment.metadata.PaymentMetadata;
import java.time.Instant;
import java.util.Optional;

/**
 * One execution attempt. {@code Payment} → Attempt 1 → Attempt 2 → ... — never overwritten, always
 * appended.
 */
public final class PaymentAttempt extends Entity<AttemptId> {

  private final int retryNumber;
  private final Instant occurredAt;
  private final Money amount;
  private final AttemptResult result;
  private final FailureReason failureReason;
  private final String gatewayReference;
  private final PaymentMetadata metadata;

  public PaymentAttempt(
      AttemptId id,
      int retryNumber,
      Instant occurredAt,
      Money amount,
      AttemptResult result,
      FailureReason failureReason,
      String gatewayReference,
      PaymentMetadata metadata) {
    super(id);
    this.retryNumber = Validate.nonNegative(retryNumber, "retryNumber must not be negative.");
    this.occurredAt = Validate.notNull(occurredAt, "occurredAt must not be null.");
    this.amount = Validate.notNull(amount, "amount must not be null.");
    this.result = Validate.notNull(result, "result must not be null.");
    this.failureReason = failureReason;
    this.gatewayReference = gatewayReference;
    this.metadata = metadata == null ? PaymentMetadata.empty() : metadata;
  }

  public int retryNumber() {
    return retryNumber;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public Money amount() {
    return amount;
  }

  public AttemptResult result() {
    return result;
  }

  public Optional<FailureReason> failureReason() {
    return Optional.ofNullable(failureReason);
  }

  public Optional<String> gatewayReference() {
    return Optional.ofNullable(gatewayReference);
  }

  public PaymentMetadata metadata() {
    return metadata;
  }

  public boolean succeeded() {
    return result == AttemptResult.SUCCEEDED;
  }
}
