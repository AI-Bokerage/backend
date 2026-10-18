package io.genfin.payment.gateway;

import static io.genfin.payment.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.payment.failure.FailureCategory;
import io.genfin.payment.failure.FailureReason;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.metadata.PaymentMetadata;
import io.genfin.payment.method.MethodBuilder;
import io.genfin.payment.method.StandardPaymentMethodType;
import io.genfin.payment.port.gateway.GatewayRegistry;
import io.genfin.payment.port.gateway.GatewaySelector;
import io.genfin.payment.port.gateway.PaymentGateway;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GatewaySpiContractTest {

  private static final class CardOnlyGateway implements PaymentGateway {
    @Override
    public GatewayResponse authorize(GatewayRequest request) {
      return GatewayResponse.success("ref", PaymentMetadata.empty());
    }

    @Override
    public GatewayResponse capture(GatewayRequest request) {
      return GatewayResponse.success("ref", PaymentMetadata.empty());
    }

    @Override
    public GatewayResponse voidAuthorization(GatewayRequest request) {
      return GatewayResponse.success("ref", PaymentMetadata.empty());
    }

    @Override
    public GatewayResponse refund(GatewayRequest request) {
      return GatewayResponse.failure(
          FailureReason.of(FailureCategory.PROVIDER_ERROR, "refunds unsupported"));
    }

    @Override
    public GatewayCapabilities capabilities() {
      return new GatewayCapabilities(
          true, true, false, true, false, Set.of(StandardPaymentMethodType.CARD));
    }

    @Override
    public GatewayHealth health() {
      return GatewayHealth.up(Instant.now());
    }

    @Override
    public PaymentProvider provider() {
      return new PaymentProvider(
          "fake-card-gw",
          "Fake Card Gateway",
          new ProviderCapabilities(Set.of("USD"), Set.of("US"), true, false));
    }
  }

  @Test
  void gatewayResponseSuccessAndFailureFactoriesAreConsistent() {
    GatewayResponse success = GatewayResponse.success("ref-1", null);
    GatewayResponse failure =
        GatewayResponse.failure(FailureReason.of(FailureCategory.TIMEOUT, "timed out"));

    assertThat(success.success()).isTrue();
    assertThat(success.reference()).contains("ref-1");
    assertThat(failure.success()).isFalse();
    assertThat(failure.failure()).isPresent();
  }

  @Test
  void selectorPicksGatewaySupportingRequestedMethod() {
    GatewayRegistry registry = GatewayRegistries.empty();
    registry.register("card-gw", new CardOnlyGateway());
    GatewaySelector selector = GatewayRegistries.firstCapable();

    GatewayRequest cardRequest =
        new GatewayRequest(
            Money.of("10.00", USD),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("**** 1")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            null);
    GatewayRequest upiRequest =
        new GatewayRequest(
            Money.of("10.00", USD),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.UPI)
                .maskedIdentifier("upi@bank")
                .build(),
            null,
            IdempotencyKey.of("k2"),
            null);

    assertThat(selector.select(cardRequest, registry)).isPresent();
    assertThat(selector.select(upiRequest, registry)).isEmpty();
  }

  @Test
  void gatewayHealthFactoriesReportUpAndDown() {
    assertThat(GatewayHealth.up(Instant.now()).healthy()).isTrue();
    assertThat(GatewayHealth.down("maintenance", Instant.now()).healthy()).isFalse();
  }
}
