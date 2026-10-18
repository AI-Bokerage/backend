package io.genfin.invoice.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.invoice.adjustment.AdjustmentEngines;
import io.genfin.invoice.calculation.InvoiceCalculators;
import io.genfin.invoice.discount.DiscountEngines;
import io.genfin.invoice.internal.builder.DefaultInvoiceBuilderProvider;
import io.genfin.invoice.internal.format.DefaultInvoiceFormatter;
import io.genfin.invoice.internal.metadata.DefaultMetadataResolver;
import io.genfin.invoice.internal.reference.DefaultReferenceResolver;
import io.genfin.invoice.lifecycle.InvoiceLifecycles;
import io.genfin.invoice.numbering.InvoiceNumberGenerators;
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
import io.genfin.invoice.validation.InvoiceValidators;

/**
 * Registers every default Invoice-engine extension so downstream code discovers them through one
 * mechanism.
 */
public final class InvoiceExtensions {

  private InvoiceExtensions() {}

  public static void registerDefaults(ExtensionRegistry registry) {
    registry.register(LifecycleProvider.class, InvoiceLifecycles.standard());
    registry.register(InvoiceCalculator.class, InvoiceCalculators.standard());
    registry.register(InvoiceValidator.class, InvoiceValidators.standard());
    registry.register(InvoiceNumberGenerator.class, InvoiceNumberGenerators.timestamp());
    registry.register(DiscountEngine.class, DiscountEngines.standard());
    registry.register(AdjustmentEngine.class, AdjustmentEngines.standard());
    registry.register(InvoiceFormatter.class, new DefaultInvoiceFormatter());
    registry.register(ReferenceResolver.class, new DefaultReferenceResolver());
    registry.register(MetadataResolver.class, new DefaultMetadataResolver());
    registry.register(InvoiceBuilderProvider.class, new DefaultInvoiceBuilderProvider());
  }
}
