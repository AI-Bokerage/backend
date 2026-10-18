package io.genfin.autoconfigure.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.payment.gateway.PaymentProvider;
import io.genfin.payment.gateway.ProviderCapabilities;
import io.genfin.payment.port.gateway.GatewayRegistry;
import io.genfin.payment.port.gateway.GatewaySelector;
import io.genfin.payment.port.gateway.PaymentGateway;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class PaymentAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  GenFinExtensionRegistryAutoConfiguration.class, PaymentAutoConfiguration.class));

  @Test
  void discoveredGatewaysArePopulatedIntoTheGatewayRegistryByProviderId() {
    contextRunner
        .withUserConfiguration(TwoGatewaysConfig.class)
        .run(
            context -> {
              GatewayRegistry registry = context.getBean(GatewayRegistry.class);
              assertThat(registry.registeredIds()).containsExactlyInAnyOrder("stripe", "razorpay");
            });
  }

  @Test
  void duplicateProviderIdsFailFast() {
    contextRunner
        .withUserConfiguration(DuplicateGatewaysConfig.class)
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void defaultGatewaySelectorIsUsedWhenNoDefaultProviderConfigured() {
    contextRunner.run(
        context -> {
          ExtensionRegistry registry = context.getBean(ExtensionRegistry.class);
          assertThat(registry.find(GatewaySelector.class)).isPresent();
          assertThat(registry.find(GatewaySelector.class).get())
              .isNotInstanceOf(NamedGatewaySelector.class);
        });
  }

  @Test
  void namedGatewaySelectorIsUsedWhenDefaultProviderConfigured() {
    contextRunner
        .withPropertyValues("genfin.payment.default-provider=stripe")
        .run(
            context -> {
              ExtensionRegistry registry = context.getBean(ExtensionRegistry.class);
              assertThat(registry.find(GatewaySelector.class).get())
                  .isInstanceOf(NamedGatewaySelector.class);
            });
  }

  private static PaymentGateway gatewayFor(String providerId) {
    PaymentGateway gateway = mock(PaymentGateway.class);
    when(gateway.provider())
        .thenReturn(
            new PaymentProvider(
                providerId,
                providerId,
                new ProviderCapabilities(Set.of(), Set.of(), false, false)));
    return gateway;
  }

  @Configuration
  static class TwoGatewaysConfig {
    @Bean
    PaymentGateway stripeGateway() {
      return gatewayFor("stripe");
    }

    @Bean
    PaymentGateway razorpayGateway() {
      return gatewayFor("razorpay");
    }
  }

  @Configuration
  static class DuplicateGatewaysConfig {
    @Bean
    PaymentGateway gatewayOne() {
      return gatewayFor("stripe");
    }

    @Bean
    PaymentGateway gatewayTwo() {
      return gatewayFor("stripe");
    }
  }
}
