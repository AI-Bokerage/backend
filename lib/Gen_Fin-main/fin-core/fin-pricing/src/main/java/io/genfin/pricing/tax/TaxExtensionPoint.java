package io.genfin.pricing.tax;

import io.genfin.api.spi.ExtensionRegistry;

/**
 * Factory/resolver for the {@link TaxPlaceholder} extension point. {@link #noOp()} is fin-pricing's
 * own default - it estimates zero tax for every line, unconditionally - kept as the fallback in
 * {@link #from} until an application registers a real Tax Engine's {@link TaxPlaceholder}. Mirrors
 * {@code io.genfin.pricing.discount.DiscountPolicies}.
 */
public final class TaxExtensionPoint {

  private TaxExtensionPoint() {}

  /** Estimates zero tax for every line - fin-pricing ships no jurisdiction tax logic itself. */
  public static TaxPlaceholder noOp() {
    return (reference, runningAmount) -> TaxEstimate.zero(reference.catalogId());
  }

  /**
   * Resolves the {@link TaxPlaceholder} registered in {@code registry} if present; otherwise falls
   * back to {@link #noOp()}.
   */
  public static TaxPlaceholder from(ExtensionRegistry registry) {
    return registry.find(TaxPlaceholder.class).orElseGet(TaxExtensionPoint::noOp);
  }
}
