package io.genfin.refund.support;

import io.genfin.payment.failure.FailureCategory;
import io.genfin.payment.failure.FailureReason;
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

/** Minimal test double for {@link PaymentGateway}, refunds-only, no network calls. */
public final class FakePaymentGateway implements PaymentGateway {

  private final boolean refundSucceeds;

  public FakePaymentGateway(boolean refundSucceeds) {
    this.refundSucceeds = refundSucceeds;
  }

  @Override
  public GatewayResponse authorize(GatewayRequest request) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GatewayResponse capture(GatewayRequest request) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GatewayResponse voidAuthorization(GatewayRequest request) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GatewayResponse refund(GatewayRequest request) {
    return refundSucceeds
        ? GatewayResponse.success("gw-refund-ref", null)
        : GatewayResponse.failure(
            FailureReason.of(FailureCategory.PROVIDER_ERROR, "declined by test gateway"));
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
        "fake", "Fake", new ProviderCapabilities(Set.of("USD"), Set.of("US"), true, true));
  }
}
