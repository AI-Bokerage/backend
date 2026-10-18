package io.genfin.autoconfigure.payment;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.payment.gateway.GatewayRegistries;
import io.genfin.payment.port.gateway.GatewayRegistry;
import io.genfin.payment.port.gateway.GatewaySelector;
import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.payment.spi.PaymentExtensions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Exposes the shared {@link GatewayRegistry}, populates it from every Spring-discovered {@link
 * PaymentGateway} bean (failing fast on duplicate provider ids), wires a {@link GatewaySelector}
 * honoring {@link PaymentProperties#getDefaultProvider()} when set, and registers every other
 * default fin-payment extension into the shared {@link ExtensionRegistry}.
 */
@AutoConfiguration
@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public GatewayRegistry gatewayRegistry(ObjectProvider<List<PaymentGateway>> gateways) {
    GatewayRegistry registry = GatewayRegistries.empty();
    Set<String> seenProviderIds = new HashSet<>();
    for (PaymentGateway gateway : gateways.getIfAvailable(List::of)) {
      String providerId = gateway.provider().providerId();
      if (!seenProviderIds.add(providerId)) {
        throw new IllegalStateException(
            "Duplicate PaymentGateway providerId registered as a Spring bean: " + providerId);
      }
      registry.register(providerId, gateway);
    }
    return registry;
  }

  @Bean
  @ConditionalOnMissingBean(name = "paymentExtensionsRegistered")
  public Boolean paymentExtensionsRegistered(
      ExtensionRegistry registry, PaymentProperties properties) {
    GatewaySelector fallback = GatewayRegistries.firstCapable();
    GatewaySelector selector =
        properties.getDefaultProvider() == null
            ? fallback
            : new NamedGatewaySelector(properties.getDefaultProvider(), fallback);
    registry.register(GatewaySelector.class, selector);

    PaymentExtensions.registerDefaults(registry);
    return Boolean.TRUE;
  }
}
