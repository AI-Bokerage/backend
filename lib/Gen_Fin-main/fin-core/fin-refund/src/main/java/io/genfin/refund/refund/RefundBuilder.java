package io.genfin.refund.refund;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.money.money.Money;
import io.genfin.refund.id.RefundId;
import io.genfin.refund.lifecycle.RefundEvent;
import io.genfin.refund.lifecycle.RefundLifecycles;
import io.genfin.refund.lifecycle.RefundStatus;
import io.genfin.refund.reference.Reference;

/**
 * Builds {@link Refund} aggregates. Preferred over the canonical constructor for readability at
 * call sites.
 */
public final class RefundBuilder {

  private RefundId id;
  private RefundNumber refundNumber;
  private Money amount;
  private RefundType type = StandardRefundType.FULL;
  private RefundDirection direction = RefundDirection.OUTBOUND;
  private Reference paymentReference;
  private StateMachine<RefundStatus, RefundEvent> lifecycle;

  private RefundBuilder() {}

  public static RefundBuilder newRefund() {
    return new RefundBuilder();
  }

  public RefundBuilder id(RefundId id) {
    this.id = id;
    return this;
  }

  public RefundBuilder refundNumber(RefundNumber refundNumber) {
    this.refundNumber = refundNumber;
    return this;
  }

  public RefundBuilder amount(Money amount) {
    this.amount = amount;
    return this;
  }

  public RefundBuilder type(RefundType type) {
    this.type = type;
    return this;
  }

  public RefundBuilder direction(RefundDirection direction) {
    this.direction = direction;
    return this;
  }

  public RefundBuilder paymentReference(Reference paymentReference) {
    this.paymentReference = paymentReference;
    return this;
  }

  public RefundBuilder lifecycle(StateMachine<RefundStatus, RefundEvent> lifecycle) {
    this.lifecycle = lifecycle;
    return this;
  }

  public Refund build() {
    StateMachine<RefundStatus, RefundEvent> resolvedLifecycle =
        lifecycle != null ? lifecycle : RefundLifecycles.requested();
    return new Refund(
        id == null ? RefundId.generate() : id,
        refundNumber,
        amount,
        type,
        direction,
        paymentReference,
        resolvedLifecycle);
  }
}
