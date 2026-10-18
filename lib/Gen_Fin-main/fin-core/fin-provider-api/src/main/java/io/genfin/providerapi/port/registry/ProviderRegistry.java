package io.genfin.providerapi.port.registry;

import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.providerapi.descriptor.ProviderDescriptor;
import io.genfin.providerapi.descriptor.ProviderId;
import java.util.List;
import java.util.Optional;

/**
 * Registry of active providers: their {@link ProviderDescriptor} plus the {@link PaymentGateway}
 * implementing them.
 */
public interface ProviderRegistry {

  void register(ProviderDescriptor descriptor, PaymentGateway gateway);

  Optional<PaymentGateway> findGateway(ProviderId id);

  Optional<ProviderDescriptor> findDescriptor(ProviderId id);

  List<ProviderDescriptor> findAll();
}
