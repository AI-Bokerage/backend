package io.genfin.pricing.port.rule;

import java.util.List;

/**
 * Supplies the {@link CommercialRule}s a deployment wants enforced. Gen-Fin ships no default
 * implementation - an application's minimum price, maximum discount, stacking limits, credit limit,
 * and regional/partner pricing rules are entirely its own to define and register. Mirrors {@code
 * io.genfin.pricing.port.catalog.CatalogItemProvider}.
 */
@FunctionalInterface
public interface CommercialRuleProvider {

  List<CommercialRule> provide();
}
