package io.genfin.payment.payment;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.time.ClockProviders;
import io.genfin.money.money.Money;
import io.genfin.payment.id.PaymentId;
import io.genfin.payment.lifecycle.PaymentEvent;
import io.genfin.payment.lifecycle.PaymentLifecycles;
import io.genfin.payment.lifecycle.PaymentState;
import io.genfin.payment.lifecycle.StandardPaymentState;
import io.genfin.payment.method.PaymentMethod;

/**
 * Builds {@link Payment} aggregates. Consumers must not call the {@code Payment} constructor
 * directly.
 */
public final class PaymentBuilder {

  private PaymentId id;
  private Money requestedAmount;
  private PaymentType type = StandardPaymentType.ONE_TIME;
  private PaymentDirection direction = PaymentDirection.INBOUND;
  private PaymentPurpose purpose = StandardPaymentPurpose.INVOICE_PAYMENT;
  private PaymentMethod method;
  private StateMachine<PaymentState, PaymentEvent> lifecycle;
  private ClockProvider clockProvider = ClockProviders.system();
  private String actor;

  private PaymentBuilder() {}

  public static PaymentBuilder newPayment() {
    return new PaymentBuilder();
  }

  public PaymentBuilder id(PaymentId id) {
    this.id = id;
    return this;
  }

  public PaymentBuilder amount(Money requestedAmount) {
    this.requestedAmount = requestedAmount;
    return this;
  }

  public PaymentBuilder type(PaymentType type) {
    this.type = type;
    return this;
  }

  public PaymentBuilder direction(PaymentDirection direction) {
    this.direction = direction;
    return this;
  }

  public PaymentBuilder purpose(PaymentPurpose purpose) {
    this.purpose = purpose;
    return this;
  }

  public PaymentBuilder method(PaymentMethod method) {
    this.method = method;
    return this;
  }

  public PaymentBuilder lifecycle(StateMachine<PaymentState, PaymentEvent> lifecycle) {
    this.lifecycle = lifecycle;
    return this;
  }

  public PaymentBuilder clockProvider(ClockProvider clockProvider) {
    this.clockProvider = clockProvider;
    return this;
  }

  public PaymentBuilder actor(String actor) {
    this.actor = actor;
    return this;
  }

  public Payment build() {
    StateMachine<PaymentState, PaymentEvent> resolvedLifecycle =
        lifecycle != null
            ? lifecycle
            : PaymentLifecycles.standard().create(StandardPaymentState.CREATED);
    return new Payment(
        id == null ? PaymentId.generate() : id,
        requestedAmount,
        type,
        direction,
        purpose,
        method,
        resolvedLifecycle,
        clockProvider,
        actor);
  }
}
