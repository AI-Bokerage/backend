package io.genfin.payment.attempt;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.money.money.Money;
import io.genfin.payment.failure.FailureReason;
import io.genfin.payment.id.AttemptId;
import io.genfin.payment.metadata.PaymentMetadata;

/**
 * Builds {@link PaymentAttempt}s. Preferred over the canonical constructor for readability at call
 * sites.
 */
public final class AttemptBuilder {

  private AttemptId id;
  private int retryNumber;
  private ClockProvider clockProvider;
  private Money amount;
  private AttemptResult result;
  private FailureReason failureReason;
  private String gatewayReference;
  private PaymentMetadata metadata = PaymentMetadata.empty();

  private AttemptBuilder() {}

  public static AttemptBuilder newAttempt() {
    return new AttemptBuilder();
  }

  public AttemptBuilder id(AttemptId id) {
    this.id = id;
    return this;
  }

  public AttemptBuilder retryNumber(int retryNumber) {
    this.retryNumber = retryNumber;
    return this;
  }

  public AttemptBuilder clockProvider(ClockProvider clockProvider) {
    this.clockProvider = clockProvider;
    return this;
  }

  public AttemptBuilder amount(Money amount) {
    this.amount = amount;
    return this;
  }

  public AttemptBuilder result(AttemptResult result) {
    this.result = result;
    return this;
  }

  public AttemptBuilder failureReason(FailureReason failureReason) {
    this.failureReason = failureReason;
    return this;
  }

  public AttemptBuilder gatewayReference(String gatewayReference) {
    this.gatewayReference = gatewayReference;
    return this;
  }

  public AttemptBuilder metadata(PaymentMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public PaymentAttempt build() {
    return new PaymentAttempt(
        id == null ? AttemptId.generate() : id,
        retryNumber,
        clockProvider.now(),
        amount,
        result,
        failureReason,
        gatewayReference,
        metadata);
  }
}
