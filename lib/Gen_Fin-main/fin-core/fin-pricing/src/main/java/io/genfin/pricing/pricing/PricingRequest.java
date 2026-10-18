package io.genfin.pricing.pricing;

import io.genfin.api.domain.AggregateRoot;
import io.genfin.api.domain.ValueObject;
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
import io.genfin.pricing.event.PricingCompleted;
import io.genfin.pricing.event.PricingRejected;
import io.genfin.pricing.event.PricingStarted;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.id.PricingResultId;
import io.genfin.pricing.lifecycle.PricingEvent;
import io.genfin.pricing.lifecycle.PricingLifecycles;
import io.genfin.pricing.lifecycle.PricingRequestEvent;
import io.genfin.pricing.lifecycle.PricingStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * "What needs to be priced?" - the aggregate root that drives a run of the Pricing Pipeline. It
 * owns only identifiers of the catalog items being priced (plus quantities); it never holds the
 * catalog items themselves, an invoice, a payment, or a ledger entry. It carries no commercial
 * value of its own - once the pipeline finishes, that value is captured in a separate, immutable
 * {@link PricingResult}.
 *
 * <p>Lifecycle transitions are driven entirely by the SPI-replaceable {@link
 * io.genfin.pricing.port.lifecycle.PricingLifecycleProvider}; this aggregate holds no transition
 * table of its own. Standard path: {@code CREATED -> VALIDATING -> CALCULATING -> PRICED}, with
 * {@code EXPIRED} and {@code REJECTED} as terminal failure states reachable from either in-flight
 * state.
 */
public final class PricingRequest extends AggregateRoot<PricingRequestId> {

  private final List<Line> lines = new ArrayList<>();
  private final Instant requestedAt;
  private final StateMachine<PricingStatus, PricingEvent> lifecycle;

  private PricingAttributes attributes;
  private PricingMetadata metadata;
  private PricingHistory history;

  public PricingRequest(PricingRequestId id, List<Line> lines, Instant requestedAt) {
    this(id, lines, requestedAt, PricingLifecycles.created());
  }

  public PricingRequest(
      PricingRequestId id,
      List<Line> lines,
      Instant requestedAt,
      StateMachine<PricingStatus, PricingEvent> lifecycle) {
    super(id);
    Validate.notNull(lines, "lines must not be null.");
    Validate.argument(!lines.isEmpty(), "lines must not be empty.");
    this.lines.addAll(lines);
    this.requestedAt = Validate.notNull(requestedAt, "requestedAt must not be null.");
    this.lifecycle = Validate.notNull(lifecycle, "lifecycle must not be null.");
    this.attributes = PricingAttributes.empty();
    this.metadata = PricingMetadata.empty();
    this.history = PricingHistory.empty().record(lifecycle.currentState().code());
  }

  public List<Line> lines() {
    return List.copyOf(lines);
  }

  public Instant requestedAt() {
    return requestedAt;
  }

  public PricingAttributes attributes() {
    return attributes;
  }

  public PricingMetadata metadata() {
    return metadata;
  }

  public PricingHistory history() {
    return history;
  }

  public PricingStatus status() {
    return lifecycle.currentState();
  }

  public void updateAttributes(PricingAttributes attributes) {
    this.attributes = Validate.notNull(attributes, "attributes must not be null.");
  }

  public void updateMetadata(PricingMetadata metadata) {
    this.metadata = Validate.notNull(metadata, "metadata must not be null.");
  }

  /** CREATED -> VALIDATING. Registers a {@link PricingStarted} event. */
  public void startValidating(ClockProvider clockProvider) {
    fire(PricingRequestEvent.VALIDATE);
    registerEvent(new PricingStarted(newMetadata(clockProvider), id()));
  }

  /** VALIDATING -> CALCULATING. */
  public void startCalculating() {
    fire(PricingRequestEvent.CALCULATE);
  }

  /** CALCULATING -> PRICED. Registers a {@link PricingCompleted} event. */
  public void complete(PricingResultId resultId, Money netAmount, ClockProvider clockProvider) {
    fire(PricingRequestEvent.COMPLETE);
    registerEvent(new PricingCompleted(newMetadata(clockProvider), id(), resultId, netAmount));
  }

  /** VALIDATING or CALCULATING -> REJECTED. Registers a {@link PricingRejected} event. */
  public void reject(String reason, ClockProvider clockProvider) {
    Validate.notBlank(reason, "reason must not be blank.");
    fire(PricingRequestEvent.REJECT, "REJECTED:" + reason);
    registerEvent(new PricingRejected(newMetadata(clockProvider), id(), reason));
  }

  /** CREATED, VALIDATING or CALCULATING -> EXPIRED. */
  public void expire() {
    fire(PricingRequestEvent.EXPIRE);
  }

  private void fire(PricingEvent event) {
    fire(event, null);
  }

  private void fire(PricingEvent event, String historyDetail) {
    TransitionResult<PricingStatus> result = lifecycle.fire(event);
    if (!result.isAllowed()) {
      throw new IllegalStateException(
          "Cannot apply "
              + event.code()
              + " while pricing request is "
              + lifecycle.currentState().code()
              + ".");
    }
    history = history.record(historyDetail != null ? historyDetail : result.currentState().code());
  }

  private EventMetadata newMetadata(ClockProvider clockProvider) {
    return new EventMetadata(
        EventId.generate(),
        OccurredAt.now(clockProvider),
        CorrelationId.generate(),
        AggregateId.of(id().value()));
  }

  /** One catalog item and the quantity of it being priced. */
  public record Line(CatalogId catalogId, int quantity) implements ValueObject {

    public Line {
      Validate.notNull(catalogId, "catalogId must not be null.");
      Validate.positive(quantity, "quantity must be positive.");
    }
  }
}
