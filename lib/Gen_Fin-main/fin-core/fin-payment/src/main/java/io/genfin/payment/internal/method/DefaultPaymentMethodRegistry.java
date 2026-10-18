package io.genfin.payment.internal.method;

import io.genfin.payment.method.PaymentMethodDescriptor;
import io.genfin.payment.method.PaymentMethodType;
import io.genfin.payment.port.method.PaymentMethodRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultPaymentMethodRegistry implements PaymentMethodRegistry {

  private final ConcurrentMap<String, PaymentMethodDescriptor> descriptors =
      new ConcurrentHashMap<>();

  @Override
  public void register(PaymentMethodDescriptor descriptor) {
    descriptors.put(descriptor.type().code(), descriptor);
  }

  @Override
  public Optional<PaymentMethodDescriptor> find(PaymentMethodType type) {
    return Optional.ofNullable(descriptors.get(type.code()));
  }

  @Override
  public List<PaymentMethodDescriptor> findAll() {
    return List.copyOf(descriptors.values());
  }
}
