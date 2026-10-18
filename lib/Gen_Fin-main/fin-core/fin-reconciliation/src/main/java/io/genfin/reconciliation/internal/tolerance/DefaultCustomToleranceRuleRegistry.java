package io.genfin.reconciliation.internal.tolerance;

import io.genfin.reconciliation.port.tolerance.CustomToleranceRule;
import io.genfin.reconciliation.port.tolerance.CustomToleranceRuleRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultCustomToleranceRuleRegistry implements CustomToleranceRuleRegistry {

  private final ConcurrentMap<String, CustomToleranceRule> rules = new ConcurrentHashMap<>();

  @Override
  public void register(CustomToleranceRule rule) {
    rules.put(rule.name(), rule);
  }

  @Override
  public Optional<CustomToleranceRule> find(String name) {
    return Optional.ofNullable(rules.get(name));
  }

  @Override
  public List<CustomToleranceRule> findAll() {
    return List.copyOf(rules.values());
  }
}
