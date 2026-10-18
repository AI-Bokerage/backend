package io.genfin.invoice.spi;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.invoice.port.adjustment.AdjustmentEngine;
import io.genfin.invoice.port.builder.InvoiceBuilderProvider;
import io.genfin.invoice.port.calculation.InvoiceCalculator;
import io.genfin.invoice.port.discount.DiscountEngine;
import io.genfin.invoice.port.format.InvoiceFormatter;
import io.genfin.invoice.port.lifecycle.LifecycleProvider;
import io.genfin.invoice.port.metadata.MetadataResolver;
import io.genfin.invoice.port.numbering.InvoiceNumberGenerator;
import io.genfin.invoice.port.reference.ReferenceResolver;
import io.genfin.invoice.port.validation.InvoiceValidator;
import org.junit.jupiter.api.Test;

class InvoiceExtensionsTest {

  @Test
  void allDefaultInvoiceExtensionsAreDiscoverableAfterRegistration() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    InvoiceExtensions.registerDefaults(registry);

    assertThat(registry.find(LifecycleProvider.class)).isPresent();
    assertThat(registry.find(InvoiceCalculator.class)).isPresent();
    assertThat(registry.find(InvoiceValidator.class)).isPresent();
    assertThat(registry.find(InvoiceNumberGenerator.class)).isPresent();
    assertThat(registry.find(DiscountEngine.class)).isPresent();
    assertThat(registry.find(AdjustmentEngine.class)).isPresent();
    assertThat(registry.find(InvoiceFormatter.class)).isPresent();
    assertThat(registry.find(ReferenceResolver.class)).isPresent();
    assertThat(registry.find(MetadataResolver.class)).isPresent();
    assertThat(registry.find(InvoiceBuilderProvider.class)).isPresent();
  }
}
