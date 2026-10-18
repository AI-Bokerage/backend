package io.genfin.reconciliation.port.tolerance;

import io.genfin.api.exception.ValidationException;
import java.util.List;
import java.util.Optional;

/**
 * Looks up a registered {@link CustomToleranceRule} by the name a {@code CustomTolerance} carries.
 */
public interface CustomToleranceRuleRegistry {

  void register(CustomToleranceRule rule);

  Optional<CustomToleranceRule> find(String name);

  default CustomToleranceRule require(String name) {
    return find(name)
        .orElseThrow(() -> new ValidationException("Unsupported custom tolerance rule: " + name));
  }

  List<CustomToleranceRule> findAll();
}
