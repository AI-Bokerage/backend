package io.genfin.autoconfigure.refund;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.refund.spi.RefundExtensions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** Registers every default fin-refund extension into the shared {@link ExtensionRegistry}. */
@AutoConfiguration
@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)
public class RefundAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "refundExtensionsRegistered")
  public Boolean refundExtensionsRegistered(ExtensionRegistry registry) {
    RefundExtensions.registerDefaults(registry);
    return Boolean.TRUE;
  }
}
