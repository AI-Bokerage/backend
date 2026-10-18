package io.genfin.refund.attempt;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.money.money.Money;
import io.genfin.payment.failure.FailureReason;
import io.genfin.refund.id.RefundAttemptId;
import io.genfin.refund.metadata.RefundMetadata;

/**
 * Builds {@link RefundAttempt}s. Preferred over the canonical constructor for readability at call
 * sites.
 */
public final class RefundAttemptBuilder {

  private RefundAttemptId id;
  private int attemptNumber;
  private ClockProvider clockProvider;
  private Money amount;
  private RefundAttemptResult result;
  private FailureReason failureReason;
  private RetryReference retryReference = RetryReference.initial();
  private String gatewayReference;
  private RefundMetadata metadata = RefundMetadata.empty();

  private RefundAttemptBuilder() {}

  public static RefundAttemptBuilder newAttempt() {
    return new RefundAttemptBuilder();
  }

  public RefundAttemptBuilder id(RefundAttemptId id) {
    this.id = id;
    return this;
  }

  public RefundAttemptBuilder attemptNumber(int attemptNumber) {
    this.attemptNumber = attemptNumber;
    return this;
  }

  public RefundAttemptBuilder clockProvider(ClockProvider clockProvider) {
    this.clockProvider = clockProvider;
    return this;
  }

  public RefundAttemptBuilder amount(Money amount) {
    this.amount = amount;
    return this;
  }

  public RefundAttemptBuilder result(RefundAttemptResult result) {
    this.result = result;
    return this;
  }

  public RefundAttemptBuilder failureReason(FailureReason failureReason) {
    this.failureReason = failureReason;
    return this;
  }

  public RefundAttemptBuilder retryReference(RetryReference retryReference) {
    this.retryReference = retryReference;
    return this;
  }

  public RefundAttemptBuilder gatewayReference(String gatewayReference) {
    this.gatewayReference = gatewayReference;
    return this;
  }

  public RefundAttemptBuilder metadata(RefundMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public RefundAttempt build() {
    return new RefundAttempt(
        id == null ? RefundAttemptId.generate() : id,
        attemptNumber,
        clockProvider.now(),
        amount,
        result,
        failureReason,
        retryReference,
        gatewayReference,
        metadata);
  }
}
