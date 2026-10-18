package io.genfin.providerapi.port.registry;

import io.genfin.api.port.spi.Extension;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.port.gateway.PaymentGateway;
import java.util.Optional;

/** The end-to-end resolution used by application code: registry lookup + selection, in one call. */
public interface ProviderResolver extends Extension {

  Optional<PaymentGateway> resolve(GatewayRequest request, ProviderRegistry registry);
}
