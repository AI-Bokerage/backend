package io.genfin.autoconfigure.reconciliation;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.reconciliation.spi.ReconciliationExtensions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers every default fin-reconciliation extension into the shared {@link ExtensionRegistry}.
 */
@AutoConfiguration
@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)
public class ReconciliationAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "reconciliationExtensionsRegistered")
  public Boolean reconciliationExtensionsRegistered(ExtensionRegistry registry) {
    ReconciliationExtensions.registerDefaults(registry);
    return Boolean.TRUE;
  }
}
