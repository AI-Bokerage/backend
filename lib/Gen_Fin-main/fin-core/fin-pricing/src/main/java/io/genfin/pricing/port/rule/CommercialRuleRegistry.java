package io.genfin.pricing.port.rule;

import java.util.List;

/**
 * Registry of the {@link CommercialRule}s an application has opted into. fin-pricing ships no
 * built-in commercial rules - an application registers its own (typically via a {@link
 * CommercialRuleProvider}) and a {@link CommercialRuleEngine} runs whatever is registered. Mirrors
 * {@code io.genfin.pricing.port.catalog.CatalogRegistry}.
 */
public interface CommercialRuleRegistry {

  void register(CommercialRule rule);

  List<CommercialRule> findAll();
}
