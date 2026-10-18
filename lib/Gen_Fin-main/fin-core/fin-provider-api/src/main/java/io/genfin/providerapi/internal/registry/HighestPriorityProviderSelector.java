package io.genfin.providerapi.internal.registry;

import io.genfin.providerapi.descriptor.ProviderDescriptor;
import io.genfin.providerapi.port.registry.ProviderSelector;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Picks the usable (ACTIVE/DEGRADED) candidate with the highest {@code ProviderPriority}. */
public final class HighestPriorityProviderSelector implements ProviderSelector {

  @Override
  public Optional<ProviderDescriptor> select(List<ProviderDescriptor> candidates) {
    return candidates.stream()
        .filter(ProviderDescriptor::isUsable)
        .max(Comparator.comparing(ProviderDescriptor::priority));
  }
}
