package io.genfin.providerapi.support;

import io.genfin.payment.gateway.GatewayCapabilities;
import io.genfin.payment.gateway.GatewayHealth;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.gateway.GatewayResponse;
import io.genfin.payment.gateway.PaymentProvider;
import io.genfin.payment.gateway.ProviderCapabilities;
import io.genfin.payment.method.StandardPaymentMethodType;
import io.genfin.payment.port.gateway.PaymentGateway;
import java.time.Instant;
import java.util.Set;

public final class FakePaymentGateway implements PaymentGateway {

  private final String providerId;

  public FakePaymentGateway(String providerId) {
    this.providerId = providerId;
  }

  @Override
  public GatewayResponse authorize(GatewayRequest request) {
    return GatewayResponse.success("auth-ref", null);
  }

  @Override
  public GatewayResponse capture(GatewayRequest request) {
    return GatewayResponse.success("cap-ref", null);
  }

  @Override
  public GatewayResponse voidAuthorization(GatewayRequest request) {
    return GatewayResponse.success("void-ref", null);
  }

  @Override
  public GatewayResponse refund(GatewayRequest request) {
    return GatewayResponse.success("refund-ref", null);
  }

  @Override
  public GatewayCapabilities capabilities() {
    return new GatewayCapabilities(
        true, true, true, true, false, Set.of(StandardPaymentMethodType.CARD));
  }

  @Override
  public GatewayHealth health() {
    return GatewayHealth.up(Instant.now());
  }

  @Override
  public PaymentProvider provider() {
    return new PaymentProvider(
        providerId, providerId, new ProviderCapabilities(Set.of("USD"), Set.of("US"), true, true));
  }
}
