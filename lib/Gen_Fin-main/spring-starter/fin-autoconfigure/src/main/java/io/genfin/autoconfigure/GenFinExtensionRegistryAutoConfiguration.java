package io.genfin.autoconfigure;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Provides the single {@link ExtensionRegistry} shared by every other Gen-Fin autoconfiguration
 * class. Every {@code XAutoConfiguration} in this module runs after this one and populates the same
 * registry instance with its module's defaults.
 */
@AutoConfiguration
public class GenFinExtensionRegistryAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ExtensionRegistry extensionRegistry() {
    return ExtensionRegistries.create();
  }
}
