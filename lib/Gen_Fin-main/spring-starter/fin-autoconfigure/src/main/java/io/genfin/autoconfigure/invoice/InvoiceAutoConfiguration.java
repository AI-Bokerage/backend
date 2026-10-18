package io.genfin.autoconfigure.invoice;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.invoice.numbering.InvoiceNumberGenerators;
import io.genfin.invoice.numbering.NumberTemplate;
import io.genfin.invoice.port.numbering.InvoiceNumberGenerator;
import io.genfin.invoice.spi.InvoiceExtensions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers every default fin-invoice extension into the shared {@link ExtensionRegistry}, honoring
 * {@link InvoiceProperties#getNumberingStrategy()} when it selects something other than
 * fin-invoice's own default ({@code timestamp}).
 */
@AutoConfiguration
@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)
@EnableConfigurationProperties(InvoiceProperties.class)
public class InvoiceAutoConfiguration {

  private static final String TIMESTAMP_STRATEGY = "timestamp";
  private static final String UUID_STRATEGY = "uuid";
  private static final String SEQUENTIAL_STRATEGY = "sequential";

  @Bean
  @ConditionalOnMissingBean(name = "invoiceExtensionsRegistered")
  public Boolean invoiceExtensionsRegistered(
      ExtensionRegistry registry, InvoiceProperties properties) {
    registerNumberGenerator(registry, properties);
    InvoiceExtensions.registerDefaults(registry);
    return Boolean.TRUE;
  }

  private void registerNumberGenerator(ExtensionRegistry registry, InvoiceProperties properties) {
    String strategy = properties.getNumberingStrategy();
    if (TIMESTAMP_STRATEGY.equals(strategy)) {
      return; // InvoiceExtensions.registerDefaults already registers this.
    }
    InvoiceNumberGenerator generator = numberGeneratorFor(strategy, properties);
    registry.register(InvoiceNumberGenerator.class, generator);
  }

  private InvoiceNumberGenerator numberGeneratorFor(String strategy, InvoiceProperties properties) {
    if (UUID_STRATEGY.equals(strategy)) {
      return InvoiceNumberGenerators.uuid();
    }
    if (SEQUENTIAL_STRATEGY.equals(strategy)) {
      String pattern = properties.getNumberingSequentialTemplate();
      if (pattern == null || pattern.isBlank()) {
        throw new IllegalStateException(
            "genfin.invoice.numbering-strategy=sequential requires"
                + " genfin.invoice.numbering-sequential-template to be set");
      }
      return InvoiceNumberGenerators.sequential(NumberTemplate.of(pattern));
    }
    throw new IllegalStateException(
        "Unknown genfin.invoice.numbering-strategy: "
            + strategy
            + " (expected one of: timestamp, sequential, uuid)");
  }
}
