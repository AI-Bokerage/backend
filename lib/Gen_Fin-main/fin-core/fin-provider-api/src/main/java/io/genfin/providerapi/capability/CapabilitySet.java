package io.genfin.providerapi.capability;

import io.genfin.api.domain.ValueObject;
import java.util.EnumSet;
import java.util.Set;

/**
 * What a provider advertises it can do — applications query this instead of hardcoding provider
 * assumptions.
 */
public record CapabilitySet(Set<ProviderCapability> capabilities) implements ValueObject {

  public CapabilitySet {
    capabilities = normalize(capabilities);
  }

  private static Set<ProviderCapability> normalize(Set<ProviderCapability> capabilities) {
    return capabilities.isEmpty()
        ? EnumSet.noneOf(ProviderCapability.class)
        : EnumSet.copyOf(capabilities);
  }

  public static CapabilitySet of(ProviderCapability... capabilities) {
    return new CapabilitySet(Set.of(capabilities));
  }

  public boolean supports(ProviderCapability capability) {
    return capabilities.contains(capability);
  }

  public boolean supportsAll(ProviderCapability... required) {
    return capabilities.containsAll(Set.of(required));
  }
}
