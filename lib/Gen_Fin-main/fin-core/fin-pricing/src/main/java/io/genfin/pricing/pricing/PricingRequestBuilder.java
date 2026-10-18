package io.genfin.pricing.pricing;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.lifecycle.PricingEvent;
import io.genfin.pricing.lifecycle.PricingLifecycles;
import io.genfin.pricing.lifecycle.PricingStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link PricingRequest}s. Preferred over the canonical constructor for readability at call
 * sites given how many optional collaborators a request carries (attributes, metadata, lifecycle)
 * on top of its required lines. Mirrors {@code io.genfin.ledger.ledger.LedgerBuilder}.
 */
public final class PricingRequestBuilder {

  private PricingRequestId id;
  private final List<PricingRequest.Line> lines = new ArrayList<>();
  private Instant requestedAt;
  private StateMachine<PricingStatus, PricingEvent> lifecycle;
  private PricingAttributes attributes = PricingAttributes.empty();
  private PricingMetadata metadata = PricingMetadata.empty();

  private PricingRequestBuilder() {}

  public static PricingRequestBuilder newRequest() {
    return new PricingRequestBuilder();
  }

  public PricingRequestBuilder id(PricingRequestId id) {
    this.id = id;
    return this;
  }

  public PricingRequestBuilder line(PricingRequest.Line line) {
    lines.add(line);
    return this;
  }

  public PricingRequestBuilder lines(List<PricingRequest.Line> lines) {
    this.lines.addAll(lines);
    return this;
  }

  public PricingRequestBuilder requestedAt(Instant requestedAt) {
    this.requestedAt = requestedAt;
    return this;
  }

  public PricingRequestBuilder lifecycle(StateMachine<PricingStatus, PricingEvent> lifecycle) {
    this.lifecycle = lifecycle;
    return this;
  }

  public PricingRequestBuilder attributes(PricingAttributes attributes) {
    this.attributes = attributes;
    return this;
  }

  public PricingRequestBuilder metadata(PricingMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public PricingRequest build() {
    PricingRequest request =
        new PricingRequest(
            id == null ? PricingRequestId.generate() : id,
            lines,
            requestedAt == null ? Instant.now() : requestedAt,
            lifecycle == null ? PricingLifecycles.created() : lifecycle);
    request.updateAttributes(attributes);
    request.updateMetadata(metadata);
    return request;
  }
}
