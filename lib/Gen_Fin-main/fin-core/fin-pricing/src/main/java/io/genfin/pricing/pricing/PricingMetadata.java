package io.genfin.pricing.pricing;

import io.genfin.api.domain.ValueObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Free-form system metadata carried on a {@link PricingRequest} or {@link PricingResult}. */
public record PricingMetadata(Map<String, String> values) implements ValueObject {

  public PricingMetadata {
    values = Map.copyOf(values);
  }

  public static PricingMetadata empty() {
    return new PricingMetadata(Map.of());
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(values.get(key));
  }

  public PricingMetadata with(String key, String value) {
    Map<String, String> merged = new LinkedHashMap<>(values);
    merged.put(key, value);
    return new PricingMetadata(merged);
  }
}
