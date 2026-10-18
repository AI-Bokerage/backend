package io.genfin.refund.refund;

import io.genfin.api.domain.AggregateRoot;
import io.genfin.api.event.EventMetadata;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.id.AggregateId;
import io.genfin.api.id.CorrelationId;
import io.genfin.api.id.EventId;
import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.TransitionResult;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.refund.event.RefundApproved;
import io.genfin.refund.event.RefundCancelled;
import io.genfin.refund.event.RefundCompleted;
import io.genfin.refund.event.RefundDisputed;
import io.genfin.refund.event.RefundFailed;
import io.genfin.refund.event.RefundRejected;
import io.genfin.refund.event.RefundRequested;
import io.genfin.refund.event.RefundReversed;
import io.genfin.refund.event.RefundStarted;
import io.genfin.refund.exception.IllegalRefundStateTransitionException;
import io.genfin.refund.id.RefundId;
import io.genfin.refund.lifecycle.RefundEvent;
import io.genfin.refund.lifecycle.RefundLifecycles;
import io.genfin.refund.lifecycle.RefundStatus;
import io.genfin.refund.lifecycle.StandardRefundEvent;
import io.genfin.refund.metadata.RefundMetadata;
import io.genfin.refund.reference.Reference;
import io.genfin.refund.reference.ReferenceCollection;
import io.genfin.refund.reference.StandardReferenceType;
import java.util.List;

/**
 * The refund aggregate root. Independent of {@code Payment} — it references the payment being
 * refunded, and optionally an invoice/customer, only through generic {@link Reference}s (never
 * fin-payment internals). Lifecycle transitions are driven entirely by the SPI-replaceable {@link
 * io.genfin.refund.port.lifecycle.RefundLifecycleProvider}; this aggregate holds no transition
 * table of its own.
 */
public final class Refund extends AggregateRoot<RefundId> {

  private final RefundNumber refundNumber;
  private final Money amount;
  private final RefundType type;
  private final RefundDirection direction;
  private final StateMachine<RefundStatus, RefundEvent> lifecycle;

  private ReferenceCollection references;
  private RefundMetadata metadata;

  public Refund(
      RefundId id,
      RefundNumber refundNumber,
      Money amount,
      RefundType type,
      RefundDirection direction,
      Reference paymentReference) {
    this(id, refundNumber, amount, type, direction, paymentReference, RefundLifecycles.requested());
  }

  public Refund(
      RefundId id,
      RefundNumber refundNumber,
      Money amount,
      RefundType type,
      RefundDirection direction,
      Reference paymentReference,
      StateMachine<RefundStatus, RefundEvent> lifecycle) {
    super(id);
    this.refundNumber = Validate.notNull(refundNumber, "refundNumber must not be null.");
    this.amount = Validate.notNull(amount, "amount must not be null.");
    Validate.argument(!amount.isNegative() && !amount.isZero(), "amount must be positive.");
    this.type = Validate.notNull(type, "type must not be null.");
    this.direction = Validate.notNull(direction, "direction must not be null.");
    Validate.notNull(paymentReference, "paymentReference must not be null.");
    Validate.argument(
        paymentReference.type().code().equals(StandardReferenceType.PAYMENT.code()),
        "paymentReference must be a PAYMENT reference.");
    this.lifecycle = Validate.notNull(lifecycle, "lifecycle must not be null.");
    this.references = ReferenceCollection.of(List.of(paymentReference));
    this.metadata = RefundMetadata.empty();
  }

  public RefundNumber refundNumber() {
    return refundNumber;
  }

  public Money amount() {
    return amount;
  }

  public RefundType type() {
    return type;
  }

  public RefundDirection direction() {
    return direction;
  }

  public RefundStatus status() {
    return lifecycle.currentState();
  }

  public ReferenceCollection references() {
    return references;
  }

  public RefundMetadata metadata() {
    return metadata;
  }

  public void addReference(Reference reference) {
    references = references.add(Validate.notNull(reference, "reference must not be null."));
  }

  public void updateMetadata(RefundMetadata metadata) {
    this.metadata = Validate.notNull(metadata, "metadata must not be null.");
  }

  public void submitForApproval(ClockProvider clockProvider) {
    fire(StandardRefundEvent.SUBMIT_FOR_APPROVAL);
    registerEvent(new RefundRequested(newMetadata(clockProvider), id(), amount));
  }

  public void approve(ClockProvider clockProvider) {
    fire(StandardRefundEvent.APPROVE);
    registerEvent(new RefundApproved(newMetadata(clockProvider), id()));
  }

  public void reject(String reason, ClockProvider clockProvider) {
    fire(StandardRefundEvent.REJECT);
    registerEvent(new RefundRejected(newMetadata(clockProvider), id(), reason));
  }

  public void startProcessing(ClockProvider clockProvider) {
    fire(StandardRefundEvent.PROCESS);
    registerEvent(new RefundStarted(newMetadata(clockProvider), id()));
  }

  public void completeProcessing(ClockProvider clockProvider) {
    fire(StandardRefundEvent.COMPLETE);
    registerEvent(new RefundCompleted(newMetadata(clockProvider), id(), amount));
  }

  public void partiallyComplete(ClockProvider clockProvider) {
    fire(StandardRefundEvent.PARTIALLY_COMPLETE);
    registerEvent(new RefundCompleted(newMetadata(clockProvider), id(), amount));
  }

  public void fail(String reason, ClockProvider clockProvider) {
    fire(StandardRefundEvent.FAIL);
    registerEvent(new RefundFailed(newMetadata(clockProvider), id(), reason));
  }

  public void cancel(String reason, ClockProvider clockProvider) {
    fire(StandardRefundEvent.CANCEL);
    registerEvent(new RefundCancelled(newMetadata(clockProvider), id(), reason));
  }

  public void reverse(ClockProvider clockProvider) {
    fire(StandardRefundEvent.REVERSE);
    registerEvent(new RefundReversed(newMetadata(clockProvider), id()));
  }

  public void dispute(String reason, ClockProvider clockProvider) {
    fire(StandardRefundEvent.DISPUTE);
    registerEvent(new RefundDisputed(newMetadata(clockProvider), id(), reason));
  }

  private void fire(RefundEvent event) {
    TransitionResult<RefundStatus> result = lifecycle.fire(event);
    if (!result.isAllowed()) {
      throw new IllegalRefundStateTransitionException(
          "Cannot apply "
              + event.code()
              + " while refund is "
              + lifecycle.currentState().code()
              + ".");
    }
  }

  private EventMetadata newMetadata(ClockProvider clockProvider) {
    return new EventMetadata(
        EventId.generate(),
        OccurredAt.now(clockProvider),
        CorrelationId.generate(),
        AggregateId.of(id().value()));
  }
}
