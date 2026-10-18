package io.genfin.autoconfigure.ledger;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.ledger.port.posting.PostingEngine;
import io.genfin.ledger.posting.PostingEngines;
import io.genfin.ledger.spi.LedgerExtensions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;

/**
 * Registers every default fin-ledger extension into the shared {@link ExtensionRegistry} and
 * exposes its one real top-level facade, {@link PostingEngine}, as a bean.
 */
@AutoConfiguration
@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)
public class LedgerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "ledgerExtensionsRegistered")
  public Boolean ledgerExtensionsRegistered(ExtensionRegistry registry) {
    LedgerExtensions.registerDefaults(registry);
    return Boolean.TRUE;
  }

  @Bean
  @ConditionalOnMissingBean
  @DependsOn("ledgerExtensionsRegistered")
  public PostingEngine postingEngine(ExtensionRegistry registry) {
    return PostingEngines.from(registry);
  }
}
