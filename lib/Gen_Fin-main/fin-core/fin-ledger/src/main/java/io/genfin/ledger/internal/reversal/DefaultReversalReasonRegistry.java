package io.genfin.ledger.internal.reversal;

import io.genfin.ledger.port.reversal.ReversalReasonRegistry;
import io.genfin.ledger.reversal.ReversalReason;
import io.genfin.ledger.reversal.ReversalReasonDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Mirrors {@code io.genfin.refund.internal.reason.DefaultRefundReasonRegistry}. */
public final class DefaultReversalReasonRegistry implements ReversalReasonRegistry {

  private final ConcurrentMap<String, ReversalReasonDescriptor> descriptors =
      new ConcurrentHashMap<>();

  @Override
  public void register(ReversalReasonDescriptor descriptor) {
    descriptors.put(descriptor.reason().code(), descriptor);
  }

  @Override
  public Optional<ReversalReasonDescriptor> find(ReversalReason reason) {
    return Optional.ofNullable(descriptors.get(reason.code()));
  }

  @Override
  public List<ReversalReasonDescriptor> findAll() {
    return List.copyOf(descriptors.values());
  }
}
