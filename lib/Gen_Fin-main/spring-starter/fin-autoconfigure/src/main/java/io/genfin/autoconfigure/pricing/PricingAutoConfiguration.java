package io.genfin.autoconfigure.pricing;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.pricing.calculation.PricingEngines;
import io.genfin.pricing.port.calculation.PricingEngine;
import io.genfin.pricing.spi.PricingExtensions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

/**
 * Registers every default fin-pricing extension into the shared {@link ExtensionRegistry} and
 * exposes its one real top-level facade, {@link PricingEngine}, as a bean.
 */
@AutoConfiguration
@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)
public class PricingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "pricingExtensionsRegistered")
  public Boolean pricingExtensionsRegistered(ExtensionRegistry registry) {
    PricingExtensions.registerDefaults(registry);
    return Boolean.TRUE;
  }

  @Bean
  @ConditionalOnMissingBean
  @DependsOn("pricingExtensionsRegistered")
  public PricingEngine pricingEngine(ExtensionRegistry registry) {
    return PricingEngines.from(registry);
  }
}
