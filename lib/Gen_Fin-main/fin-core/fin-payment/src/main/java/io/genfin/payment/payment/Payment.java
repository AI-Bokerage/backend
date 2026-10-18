package io.genfin.payment.payment;

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
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import io.genfin.payment.attempt.AttemptResult;
import io.genfin.payment.attempt.PaymentAttempt;
import io.genfin.payment.authorization.Authorization;
import io.genfin.payment.authorization.Capture;
import io.genfin.payment.authorization.ReleaseAction;
import io.genfin.payment.authorization.VoidAction;
import io.genfin.payment.event.PaymentAuthorized;
import io.genfin.payment.event.PaymentCancelled;
import io.genfin.payment.event.PaymentCaptured;
import io.genfin.payment.event.PaymentCreated;
import io.genfin.payment.event.PaymentDisputed;
import io.genfin.payment.event.PaymentExpired;
import io.genfin.payment.event.PaymentFailed;
import io.genfin.payment.event.PaymentRefundRequested;
import io.genfin.payment.event.PaymentRefunded;
import io.genfin.payment.event.PaymentSettled;
import io.genfin.payment.exception.CaptureExceedsAuthorizationException;
import io.genfin.payment.exception.IllegalPaymentStateTransitionException;
import io.genfin.payment.failure.FailureReason;
import io.genfin.payment.id.AttemptId;
import io.genfin.payment.id.PaymentId;
import io.genfin.payment.lifecycle.PaymentEvent;
import io.genfin.payment.lifecycle.PaymentState;
import io.genfin.payment.lifecycle.StandardPaymentEvent;
import io.genfin.payment.metadata.PaymentMetadata;
import io.genfin.payment.method.PaymentMethod;
import io.genfin.payment.reference.Reference;
import io.genfin.payment.reference.ReferenceCollection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The payment aggregate root. References an invoice/customer/order only through generic {@link
 * Reference}s — never through Invoice Engine internals. Owns no provider concepts.
 */
public final class Payment extends AggregateRoot<PaymentId> {

  private final Money requestedAmount;
  private final PaymentType type;
  private final PaymentDirection direction;
  private final PaymentPurpose purpose;
  private final PaymentMethod method;
  private final StateMachine<PaymentState, PaymentEvent> lifecycle;
  private final List<PaymentAttempt> attempts = new ArrayList<>();
  private final List<Capture> captures = new ArrayList<>();

  private ReferenceCollection references;
  private PaymentMetadata metadata;
  private Authorization authorization;
  private VoidAction voidAction;
  private ReleaseAction releaseAction;
  private Money amountRefunded;
  private AuditInfo audit;
  private int version;

  Payment(
      PaymentId id,
      Money requestedAmount,
      PaymentType type,
      PaymentDirection direction,
      PaymentPurpose purpose,
      PaymentMethod method,
      StateMachine<PaymentState, PaymentEvent> lifecycle,
      ClockProvider clockProvider,
      String actor) {
    super(id);
    this.requestedAmount = Validate.notNull(requestedAmount, "requestedAmount must not be null.");
    Validate.argument(
        !requestedAmount.isNegative() && !requestedAmount.isZero(),
        "requestedAmount must be positive.");
    this.type = Validate.notNull(type, "type must not be null.");
    this.direction = Validate.notNull(direction, "direction must not be null.");
    this.purpose = Validate.notNull(purpose, "purpose must not be null.");
    this.method = Validate.notNull(method, "method must not be null.");
    this.lifecycle = Validate.notNull(lifecycle, "lifecycle must not be null.");
    this.references = ReferenceCollection.empty();
    this.metadata = PaymentMetadata.empty();
    this.amountRefunded = Money.zero(requestedAmount.currency());
    this.version = 0;
    var now = clockProvider.now();
    this.audit = new AuditInfo(now, now, actor, actor);
    registerEvent(new PaymentCreated(newMetadata(clockProvider), id));
  }

  public Money requestedAmount() {
    return requestedAmount;
  }

  public Currency currency() {
    return requestedAmount.currency();
  }

  public PaymentType type() {
    return type;
  }

  public PaymentDirection direction() {
    return direction;
  }

  public PaymentPurpose purpose() {
    return purpose;
  }

  public PaymentMethod method() {
    return method;
  }

  public PaymentState status() {
    return lifecycle.currentState();
  }

  public ReferenceCollection references() {
    return references;
  }

  public PaymentMetadata metadata() {
    return metadata;
  }

  public List<PaymentAttempt> attempts() {
    return List.copyOf(attempts);
  }

  public List<Capture> captures() {
    return List.copyOf(captures);
  }

  public Optional<Authorization> authorization() {
    return Optional.ofNullable(authorization);
  }

  public Optional<VoidAction> voidAction() {
    return Optional.ofNullable(voidAction);
  }

  public Optional<ReleaseAction> releaseAction() {
    return Optional.ofNullable(releaseAction);
  }

  public Money amountRefunded() {
    return amountRefunded;
  }

  public AuditInfo audit() {
    return audit;
  }

  public int version() {
    return version;
  }

  public Money totalCaptured() {
    Money total = Money.zero(requestedAmount.currency());
    for (Capture capture : captures) {
      total = total.add(capture.amount());
    }
    return total;
  }

  public void addReference(Reference reference, ClockProvider clockProvider, String actor) {
    references = references.add(reference);
    touch(clockProvider, actor);
  }

  public void updateMetadata(PaymentMetadata metadata, ClockProvider clockProvider, String actor) {
    this.metadata = metadata;
    touch(clockProvider, actor);
  }

  public void submit(ClockProvider clockProvider, String actor) {
    fire(StandardPaymentEvent.SUBMIT);
    touch(clockProvider, actor);
  }

  public PaymentAttempt recordAttempt(
      Money amount,
      AttemptResult result,
      FailureReason failureReason,
      String gatewayReference,
      ClockProvider clockProvider,
      String actor) {
    PaymentAttempt attempt =
        new PaymentAttempt(
            AttemptId.generate(),
            attempts.size(),
            clockProvider.now(),
            amount,
            result,
            failureReason,
            gatewayReference,
            PaymentMetadata.empty());
    attempts.add(attempt);
    touch(clockProvider, actor);
    return attempt;
  }

  public void authorize(
      Money amount, String gatewayReference, ClockProvider clockProvider, String actor) {
    boolean full = amount.compareTo(requestedAmount) >= 0;
    fire(full ? StandardPaymentEvent.AUTHORIZE : StandardPaymentEvent.PARTIALLY_AUTHORIZE);
    this.authorization = new Authorization(amount, clockProvider.now(), gatewayReference);
    registerEvent(new PaymentAuthorized(newMetadata(clockProvider), id(), amount));
    touch(clockProvider, actor);
  }

  public void capture(
      Money amount, String gatewayReference, ClockProvider clockProvider, String actor) {
    Money authorizedAmount =
        authorization().map(Authorization::amount).orElse(Money.zero(requestedAmount.currency()));
    Money alreadyCaptured = totalCaptured();
    if (alreadyCaptured.add(amount).compareTo(authorizedAmount) > 0) {
      throw new CaptureExceedsAuthorizationException(
          "Capture of "
              + amount
              + " would exceed authorized amount "
              + authorizedAmount
              + " (already captured "
              + alreadyCaptured
              + ").");
    }
    boolean full = alreadyCaptured.add(amount).compareTo(authorizedAmount) >= 0;
    fire(full ? StandardPaymentEvent.CAPTURE : StandardPaymentEvent.PARTIALLY_CAPTURE);
    captures.add(new Capture(amount, clockProvider.now(), gatewayReference, !full));
    registerEvent(new PaymentCaptured(newMetadata(clockProvider), id(), amount));
    touch(clockProvider, actor);
  }

  public void release(Money amount, String reason, ClockProvider clockProvider, String actor) {
    this.releaseAction = new ReleaseAction(amount, clockProvider.now(), reason);
    touch(clockProvider, actor);
  }

  public void settle(ClockProvider clockProvider, String actor) {
    fire(StandardPaymentEvent.SETTLE);
    registerEvent(new PaymentSettled(newMetadata(clockProvider), id(), totalCaptured()));
    touch(clockProvider, actor);
  }

  public void fail(FailureReason reason, ClockProvider clockProvider, String actor) {
    fire(StandardPaymentEvent.FAIL);
    registerEvent(new PaymentFailed(newMetadata(clockProvider), id(), reason));
    touch(clockProvider, actor);
  }

  public void cancel(String reason, ClockProvider clockProvider, String actor) {
    fire(StandardPaymentEvent.CANCEL);
    registerEvent(new PaymentCancelled(newMetadata(clockProvider), id(), reason));
    touch(clockProvider, actor);
  }

  public void voidAuthorization(String reason, ClockProvider clockProvider, String actor) {
    fire(StandardPaymentEvent.CANCEL);
    this.voidAction = new VoidAction(clockProvider.now(), reason);
    registerEvent(new PaymentCancelled(newMetadata(clockProvider), id(), reason));
    touch(clockProvider, actor);
  }

  public void expire(ClockProvider clockProvider, String actor) {
    fire(StandardPaymentEvent.EXPIRE);
    registerEvent(new PaymentExpired(newMetadata(clockProvider), id()));
    touch(clockProvider, actor);
  }

  public void requestRefund(Money amount, ClockProvider clockProvider, String actor) {
    registerEvent(new PaymentRefundRequested(newMetadata(clockProvider), id(), amount));
    touch(clockProvider, actor);
  }

  public void refund(Money amount, ClockProvider clockProvider, String actor) {
    Money newTotal = amountRefunded.add(amount);
    boolean full = newTotal.compareTo(totalCaptured()) >= 0;
    fire(full ? StandardPaymentEvent.REFUND : StandardPaymentEvent.PARTIALLY_REFUND);
    this.amountRefunded = newTotal;
    registerEvent(new PaymentRefunded(newMetadata(clockProvider), id(), amount));
    touch(clockProvider, actor);
  }

  public void dispute(String reason, ClockProvider clockProvider, String actor) {
    fire(StandardPaymentEvent.DISPUTE);
    registerEvent(new PaymentDisputed(newMetadata(clockProvider), id(), reason));
    touch(clockProvider, actor);
  }

  public void markChargeback(ClockProvider clockProvider, String actor) {
    fire(StandardPaymentEvent.MARK_CHARGEBACK);
    touch(clockProvider, actor);
  }

  private void fire(PaymentEvent event) {
    TransitionResult<PaymentState> result = lifecycle.fire(event);
    if (!result.isAllowed()) {
      throw new IllegalPaymentStateTransitionException(
          "Cannot apply "
              + event.code()
              + " while payment is "
              + lifecycle.currentState().code()
              + ".");
    }
  }

  private void touch(ClockProvider clockProvider, String actor) {
    version++;
    audit = audit.touched(clockProvider.now(), actor);
  }

  private EventMetadata newMetadata(ClockProvider clockProvider) {
    return new EventMetadata(
        EventId.generate(),
        OccurredAt.now(clockProvider),
        CorrelationId.generate(),
        AggregateId.of(id().value()));
  }
}
