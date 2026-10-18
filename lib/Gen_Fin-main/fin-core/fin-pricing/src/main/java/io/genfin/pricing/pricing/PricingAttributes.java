package io.genfin.pricing.pricing;

import io.genfin.api.domain.ValueObject;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Application-supplied input attributes (e.g. customer segment, region, channel) that pricing
 * pipeline stages such as the Discount/Promotion/Coupon Engines read to decide which rules apply.
 * Deliberately its own type, distinct from {@link PricingMetadata}: attributes are pricing
 * <em>inputs</em>, metadata is system-recorded bookkeeping.
 */
public record PricingAttributes(Map<String, String> values) implements ValueObject {

  public PricingAttributes {
    values = Map.copyOf(values);
  }

  public static PricingAttributes empty() {
    return new PricingAttributes(Map.of());
  }

  public Optional<String> find(String key) {
    return Optional.ofNullable(values.get(key));
  }

  public PricingAttributes with(String key, String value) {
    Map<String, String> merged = new LinkedHashMap<>(values);
    merged.put(key, value);
    return new PricingAttributes(merged);
  }
}
