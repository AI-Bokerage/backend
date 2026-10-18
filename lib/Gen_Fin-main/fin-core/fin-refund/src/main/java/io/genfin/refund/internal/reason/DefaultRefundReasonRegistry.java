package io.genfin.refund.internal.reason;

import io.genfin.refund.port.reason.RefundReasonRegistry;
import io.genfin.refund.reason.RefundReason;
import io.genfin.refund.reason.RefundReasonDescriptor;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultRefundReasonRegistry implements RefundReasonRegistry {

  private final ConcurrentMap<String, RefundReasonDescriptor> descriptors =
      new ConcurrentHashMap<>();

  @Override
  public void register(RefundReasonDescriptor descriptor) {
    descriptors.put(descriptor.reason().code(), descriptor);
  }

  @Override
  public Optional<RefundReasonDescriptor> find(RefundReason reason) {
    return Optional.ofNullable(descriptors.get(reason.code()));
  }

  @Override
  public List<RefundReasonDescriptor> findAll() {
    return List.copyOf(descriptors.values());
  }
}
