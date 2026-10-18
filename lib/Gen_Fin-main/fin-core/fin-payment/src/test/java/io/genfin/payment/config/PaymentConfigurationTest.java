package io.genfin.payment.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentConfigurationTest {

  @Test
  void standardConfigurationHasExplicitDefaultsForEveryPolicy() {
    PaymentConfiguration configuration = PaymentConfigurations.standard();

    assertThat(configuration.lifecycleProvider()).isNotNull();
    assertThat(configuration.gatewayRegistry()).isNotNull();
    assertThat(configuration.gatewaySelector()).isNotNull();
    assertThat(configuration.methodRegistry()).isNotNull();
    assertThat(configuration.captureStrategy().isAutomaticCapture()).isTrue();
    assertThat(configuration.authorizationStrategy().requiresFullAuthorization()).isTrue();
    assertThat(configuration.autoSettle()).isTrue();
    assertThat(configuration.sessionTimeout()).isNotNull();
    assertThat(configuration.maxRetryAttempts()).isEqualTo(3);
  }
}
