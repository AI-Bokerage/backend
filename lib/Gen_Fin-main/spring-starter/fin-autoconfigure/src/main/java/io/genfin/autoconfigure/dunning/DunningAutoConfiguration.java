package io.genfin.autoconfigure.dunning;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.dunning.spi.DunningExtensions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Registers every default fin-dunning extension into the shared {@link ExtensionRegistry}. */
@AutoConfiguration
@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)
@EnableConfigurationProperties(DunningProperties.class)
@ConditionalOnProperty(prefix = "genfin.dunning", name = "enabled", matchIfMissing = true)
public class DunningAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "dunningExtensionsRegistered")
  public Boolean dunningExtensionsRegistered(ExtensionRegistry registry) {
    DunningExtensions.registerDefaults(registry);
    return Boolean.TRUE;
  }
}
