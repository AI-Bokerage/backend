package io.genfin.pricing.tax;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.money.Money;

/**
 * The Tax Placeholder pipeline stage's single extension point - explicitly NOT a tax engine. It
 * calculates no jurisdiction-specific tax (no GST/VAT/IGST/CGST, no jurisdiction rules) itself;
 * fin-pricing ships only {@link TaxExtensionPoint#noOp()}, which always returns {@link
 * TaxEstimate#zero}. A future Tax Engine module registers its real implementation against this
 * interface via {@code io.genfin.api.spi.ExtensionRegistry}, exactly as any other fin-pricing
 * Extension is registered.
 */
@FunctionalInterface
public interface TaxPlaceholder extends Extension {

  /**
   * Estimates tax for one line, given a typed {@link TaxContextReference} and its running price.
   */
  TaxEstimate estimate(TaxContextReference reference, Money runningAmount);
}
