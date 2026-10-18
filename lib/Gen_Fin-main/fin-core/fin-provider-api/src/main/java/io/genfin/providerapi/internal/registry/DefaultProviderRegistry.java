package io.genfin.providerapi.internal.registry;

import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.providerapi.descriptor.ProviderDescriptor;
import io.genfin.providerapi.descriptor.ProviderId;
import io.genfin.providerapi.port.registry.ProviderRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultProviderRegistry implements ProviderRegistry {

  private final Map<ProviderId, ProviderDescriptor> descriptors = new ConcurrentHashMap<>();
  private final Map<ProviderId, PaymentGateway> gateways = new ConcurrentHashMap<>();

  @Override
  public void register(ProviderDescriptor descriptor, PaymentGateway gateway) {
    descriptors.put(descriptor.id(), descriptor);
    gateways.put(descriptor.id(), gateway);
  }

  @Override
  public Optional<PaymentGateway> findGateway(ProviderId id) {
    return Optional.ofNullable(gateways.get(id));
  }

  @Override
  public Optional<ProviderDescriptor> findDescriptor(ProviderId id) {
    return Optional.ofNullable(descriptors.get(id));
  }

  @Override
  public List<ProviderDescriptor> findAll() {
    return List.copyOf(descriptors.values());
  }
}
