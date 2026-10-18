package io.genfin.autoconfigure.health;

import io.genfin.document.port.DocumentRenderer;
import io.genfin.payment.port.gateway.GatewayRegistry;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Reports which payment providers/custom document renderers are registered. Inert (no bean
 * produced) unless the consuming application already has spring-boot-starter-actuator on the
 * classpath. Never makes a network call.
 */
@AutoConfiguration
@ConditionalOnClass(HealthIndicator.class)
public class HealthAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public HealthIndicator genFinHealthIndicator(
      ObjectProvider<GatewayRegistry> gatewayRegistry,
      ObjectProvider<List<DocumentRenderer>> customRenderers) {
    return () -> {
      Health.Builder health = Health.up();
      gatewayRegistry.ifAvailable(
          registry -> health.withDetail("paymentProvidersLoaded", registry.registeredIds().size()));
      health.withDetail(
          "customDocumentRenderersLoaded", customRenderers.getIfAvailable(List::of).size());
      return health.build();
    };
  }
}
