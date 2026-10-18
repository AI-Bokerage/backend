package io.genfin.pricing.pricing;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.ArrayList;
import java.util.List;

/** An append-only audit trail of what happened to a {@link PricingRequest}. */
public record PricingHistory(List<String> entries) implements ValueObject {

  public PricingHistory {
    entries = List.copyOf(entries);
  }

  public static PricingHistory empty() {
    return new PricingHistory(List.of());
  }

  public PricingHistory record(String event) {
    Validate.notBlank(event, "event must not be blank.");
    List<String> next = new ArrayList<>(entries);
    next.add(event);
    return new PricingHistory(next);
  }
}
