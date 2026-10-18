package io.genfin.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.payment.method.StandardPaymentMethodType;
import io.genfin.payment.port.gateway.GatewayRegistry;
import io.genfin.payment.port.gateway.PaymentGateway;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ConcurrentGatewayRegistryTest {

  private static PaymentGateway fakeGateway(String providerId) {
    return new PaymentGateway() {
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
            providerId,
            providerId,
            new ProviderCapabilities(Set.of("USD"), Set.of("US"), true, true));
      }
    };
  }

  @Test
  void concurrentRegistrationIsThreadSafe() throws Exception {
    GatewayRegistry registry = GatewayRegistries.empty();

    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      for (int i = 0; i < 50; i++) {
        String id = "gateway-" + i;
        pool.submit(() -> registry.register(id, fakeGateway(id)));
      }
    }

    assertThat(registry.registeredIds()).hasSize(50);
  }
}
