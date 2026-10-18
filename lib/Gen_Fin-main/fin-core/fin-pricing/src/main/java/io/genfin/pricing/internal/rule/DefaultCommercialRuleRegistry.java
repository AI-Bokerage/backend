package io.genfin.pricing.internal.rule;

import io.genfin.pricing.port.rule.CommercialRule;
import io.genfin.pricing.port.rule.CommercialRuleRegistry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultCommercialRuleRegistry implements CommercialRuleRegistry {

  private final List<CommercialRule> rules = new CopyOnWriteArrayList<>();

  @Override
  public void register(CommercialRule rule) {
    rules.add(rule);
  }

  @Override
  public List<CommercialRule> findAll() {
    return List.copyOf(rules);
  }
}
