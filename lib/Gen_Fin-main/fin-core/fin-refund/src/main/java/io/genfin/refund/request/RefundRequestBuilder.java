package io.genfin.refund.request;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.time.ClockProviders;
import io.genfin.money.money.Money;
import io.genfin.refund.id.RefundRequestId;
import io.genfin.refund.reason.RefundReason;
import io.genfin.refund.reference.Reference;
import java.time.Instant;

/**
 * Builds {@link RefundRequest}s. Preferred over the canonical constructor for readability at call
 * sites.
 */
public final class RefundRequestBuilder {

  private RefundRequestId id;
  private Money amount;
  private RefundReason reason;
  private Reference paymentReference;
  private Instant requestedAt;
  private ClockProvider clockProvider = ClockProviders.system();

  private RefundRequestBuilder() {}

  public static RefundRequestBuilder newRequest() {
    return new RefundRequestBuilder();
  }

  public RefundRequestBuilder id(RefundRequestId id) {
    this.id = id;
    return this;
  }

  public RefundRequestBuilder amount(Money amount) {
    this.amount = amount;
    return this;
  }

  public RefundRequestBuilder reason(RefundReason reason) {
    this.reason = reason;
    return this;
  }

  public RefundRequestBuilder paymentReference(Reference paymentReference) {
    this.paymentReference = paymentReference;
    return this;
  }

  public RefundRequestBuilder requestedAt(Instant requestedAt) {
    this.requestedAt = requestedAt;
    return this;
  }

  public RefundRequestBuilder clockProvider(ClockProvider clockProvider) {
    this.clockProvider = clockProvider;
    return this;
  }

  public RefundRequest build() {
    return new RefundRequest(
        id == null ? RefundRequestId.generate() : id,
        amount,
        reason,
        paymentReference,
        requestedAt == null ? clockProvider.now() : requestedAt);
  }
}
