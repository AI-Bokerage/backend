package io.genfin.pricing.rule;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.rule.DefaultCommercialRuleEngine;
import io.genfin.pricing.port.rule.CommercialRule;
import io.genfin.pricing.port.rule.CommercialRuleEngine;
import io.genfin.pricing.port.rule.CommercialRuleRegistry;
import java.util.List;

/**
 * Factory for {@link CommercialRuleEngine} instances. Deliberately has no standard-rules
 * counterpart - Gen-Fin defines no built-in commercial rules, so an engine carries only whatever an
 * application registers. Mirrors {@code io.genfin.reconciliation.rule.RuleEngines}.
 */
public final class CommercialRuleEngines {

  private CommercialRuleEngines() {}

  public static CommercialRuleEngine of(List<CommercialRule> rules) {
    return new DefaultCommercialRuleEngine(rules);
  }

  public static CommercialRuleEngine of(CommercialRuleRegistry registry) {
    return of(registry.findAll());
  }

  /** An engine carrying no rules at all - the starting point before any are registered. */
  public static CommercialRuleEngine empty() {
    return of(List.of());
  }

  /**
   * Resolves the {@link CommercialRuleEngine} registered in {@code registry}, or {@link #empty}.
   */
  public static CommercialRuleEngine from(ExtensionRegistry registry) {
    return registry.find(CommercialRuleEngine.class).orElseGet(CommercialRuleEngines::empty);
  }
}
