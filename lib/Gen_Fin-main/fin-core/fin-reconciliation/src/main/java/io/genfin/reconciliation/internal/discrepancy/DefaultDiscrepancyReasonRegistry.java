package io.genfin.reconciliation.internal.discrepancy;

import io.genfin.reconciliation.discrepancy.DiscrepancyReason;
import io.genfin.reconciliation.discrepancy.DiscrepancyReasonDescriptor;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyReasonRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultDiscrepancyReasonRegistry implements DiscrepancyReasonRegistry {

  private final ConcurrentMap<String, DiscrepancyReasonDescriptor> descriptors =
      new ConcurrentHashMap<>();

  @Override
  public void register(DiscrepancyReasonDescriptor descriptor) {
    descriptors.put(descriptor.reason().code(), descriptor);
  }

  @Override
  public Optional<DiscrepancyReasonDescriptor> find(DiscrepancyReason reason) {
    return Optional.ofNullable(descriptors.get(reason.code()));
  }

  @Override
  public List<DiscrepancyReasonDescriptor> findAll() {
    return List.copyOf(descriptors.values());
  }
}
