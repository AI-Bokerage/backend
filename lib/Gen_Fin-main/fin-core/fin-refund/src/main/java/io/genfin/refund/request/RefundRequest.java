package io.genfin.refund.request;

import io.genfin.api.domain.AggregateRoot;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.refund.exception.IllegalRefundStateTransitionException;
import io.genfin.refund.id.RefundRequestId;
import io.genfin.refund.reason.RefundReason;
import io.genfin.refund.reference.Reference;
import io.genfin.refund.reference.ReferenceCollection;
import io.genfin.refund.reference.StandardReferenceType;
import java.time.Instant;
import java.util.List;

/**
 * A request to refund a payment, raised before any {@link io.genfin.refund.refund.Refund} is
 * attempted or executed. Independent of {@code Refund} — it holds no reference to one; an
 * application that later executes the refund links the two itself (e.g. by adding a reference to
 * this request's {@link RefundRequestId} on the resulting {@code Refund}).
 */
public final class RefundRequest extends AggregateRoot<RefundRequestId> {

  private final Money amount;
  private final RefundReason reason;
  private final Instant requestedAt;

  private ReferenceCollection references;
  private RefundRequestMetadata metadata;
  private RefundRequestStatus status;

  public RefundRequest(
      RefundRequestId id,
      Money amount,
      RefundReason reason,
      Reference paymentReference,
      Instant requestedAt) {
    super(id);
    this.amount = Validate.notNull(amount, "amount must not be null.");
    Validate.argument(!amount.isNegative() && !amount.isZero(), "amount must be positive.");
    this.reason = Validate.notNull(reason, "reason must not be null.");
    Validate.notNull(paymentReference, "paymentReference must not be null.");
    Validate.argument(
        paymentReference.type().code().equals(StandardReferenceType.PAYMENT.code()),
        "paymentReference must be a PAYMENT reference.");
    this.requestedAt = Validate.notNull(requestedAt, "requestedAt must not be null.");
    this.references = ReferenceCollection.of(List.of(paymentReference));
    this.metadata = RefundRequestMetadata.empty();
    this.status = RefundRequestStatus.PENDING;
  }

  public Money amount() {
    return amount;
  }

  public RefundReason reason() {
    return reason;
  }

  public Instant requestedAt() {
    return requestedAt;
  }

  public ReferenceCollection references() {
    return references;
  }

  public RefundRequestMetadata metadata() {
    return metadata;
  }

  public RefundRequestStatus status() {
    return status;
  }

  public void addReference(Reference reference) {
    references = references.add(Validate.notNull(reference, "reference must not be null."));
  }

  public void updateMetadata(RefundRequestMetadata metadata) {
    this.metadata = Validate.notNull(metadata, "metadata must not be null.");
  }

  /** PENDING -> APPROVED. */
  public void approve() {
    transition(RefundRequestStatus.PENDING, RefundRequestStatus.APPROVED);
  }

  /** PENDING -> REJECTED. */
  public void reject() {
    transition(RefundRequestStatus.PENDING, RefundRequestStatus.REJECTED);
  }

  /** PENDING or APPROVED -> CANCELLED. */
  public void cancel() {
    if (status != RefundRequestStatus.PENDING && status != RefundRequestStatus.APPROVED) {
      throw illegalTransition(RefundRequestStatus.CANCELLED);
    }
    status = RefundRequestStatus.CANCELLED;
  }

  /** APPROVED -> FULFILLED, once the actual {@code Refund} has been executed. */
  public void fulfill() {
    transition(RefundRequestStatus.APPROVED, RefundRequestStatus.FULFILLED);
  }

  private void transition(RefundRequestStatus expected, RefundRequestStatus next) {
    if (status != expected) {
      throw illegalTransition(next);
    }
    status = next;
  }

  private IllegalRefundStateTransitionException illegalTransition(RefundRequestStatus next) {
    return new IllegalRefundStateTransitionException(
        "Cannot move refund request to " + next + " while it is " + status + ".");
  }
}
