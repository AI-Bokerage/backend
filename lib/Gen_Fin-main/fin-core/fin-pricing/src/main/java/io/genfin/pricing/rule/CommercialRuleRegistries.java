package io.genfin.pricing.rule;

import io.genfin.pricing.internal.rule.DefaultCommercialRuleRegistry;
import io.genfin.pricing.port.rule.CommercialRuleProvider;
import io.genfin.pricing.port.rule.CommercialRuleRegistry;

/**
 * Factory for {@link CommercialRuleRegistry} instances. Mirrors {@code
 * io.genfin.pricing.catalog.CatalogRegistries}.
 */
public final class CommercialRuleRegistries {

  private CommercialRuleRegistries() {}

  public static CommercialRuleRegistry empty() {
    return new DefaultCommercialRuleRegistry();
  }

  public static CommercialRuleRegistry withProvider(CommercialRuleProvider provider) {
    CommercialRuleRegistry registry = empty();
    provider.provide().forEach(registry::register);
    return registry;
  }
}
