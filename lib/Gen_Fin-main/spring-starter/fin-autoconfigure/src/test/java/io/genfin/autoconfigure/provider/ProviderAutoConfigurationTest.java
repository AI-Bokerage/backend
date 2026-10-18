package io.genfin.autoconfigure.provider;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.providerapi.port.registry.ProviderResolver;
import io.genfin.razorpay.gateway.RazorpayGateway;
import io.genfin.stripe.gateway.StripeGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProviderAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GenFinExtensionRegistryAutoConfiguration.class, ProviderAutoConfiguration.class));

  @Test
  void providerApiDefaultsRegisteredEvenWithoutAnyProviderConfigured() {
    contextRunner.run(
        context ->
            assertThat(
                    context
                        .getBean(io.genfin.api.spi.ExtensionRegistry.class)
                        .find(ProviderResolver.class))
                .isPresent());
  }

  @Test
  void noGatewayBeansProducedWithoutProviderProperties() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(PaymentGateway.class));
  }

  @Test
  void stripeGatewayProducedWhenApiKeyConfigured() {
    contextRunner
        .withPropertyValues(
            "genfin.payment.providers.stripe.api-key=sk_test_123",
            "genfin.payment.providers.stripe.webhook-secret=whsec_123")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean("stripePaymentGateway")).isInstanceOf(StripeGateway.class);
            });
  }

  @Test
  void razorpayGatewayProducedWhenKeyIdConfigured() {
    contextRunner
        .withPropertyValues(
            "genfin.payment.providers.razorpay.key-id=rzp_test_123",
            "genfin.payment.providers.razorpay.key-secret=secret",
            "genfin.payment.providers.razorpay.webhook-secret=whsec_123")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean("razorpayPaymentGateway"))
                  .isInstanceOf(RazorpayGateway.class);
            });
  }
}
