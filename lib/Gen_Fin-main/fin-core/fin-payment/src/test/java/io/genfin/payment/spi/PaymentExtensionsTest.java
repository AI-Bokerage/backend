package io.genfin.payment.spi;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.payment.port.authorization.AuthorizationStrategy;
import io.genfin.payment.port.authorization.CaptureStrategy;
import io.genfin.payment.port.calculation.PaymentCalculator;
import io.genfin.payment.port.gateway.GatewaySelector;
import io.genfin.payment.port.idempotency.IdempotencyValidator;
import io.genfin.payment.port.lifecycle.LifecycleProvider;
import io.genfin.payment.port.method.PaymentMethodProvider;
import io.genfin.payment.port.validation.PaymentValidator;
import org.junit.jupiter.api.Test;

class PaymentExtensionsTest {

  @Test
  void allDefaultPaymentExtensionsAreDiscoverableAfterRegistration() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    PaymentExtensions.registerDefaults(registry);

    assertThat(registry.find(LifecycleProvider.class)).isPresent();
    assertThat(registry.find(PaymentCalculator.class)).isPresent();
    assertThat(registry.find(PaymentValidator.class)).isPresent();
    assertThat(registry.find(PaymentMethodProvider.class)).isPresent();
    assertThat(registry.find(GatewaySelector.class)).isPresent();
    assertThat(registry.find(CaptureStrategy.class)).isPresent();
    assertThat(registry.find(AuthorizationStrategy.class)).isPresent();
    assertThat(registry.find(IdempotencyValidator.class)).isPresent();
  }
}
