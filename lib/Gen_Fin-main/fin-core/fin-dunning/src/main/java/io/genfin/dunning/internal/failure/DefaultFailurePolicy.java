package io.genfin.dunning.internal.failure;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.failure.FailureClassificationRule;
import io.genfin.dunning.port.failure.FailurePolicy;
import java.util.List;

/**
 * Adapts a resolved list of {@link FailureClassificationRule}s to the {@link FailurePolicy} port.
 */
public final class DefaultFailurePolicy implements FailurePolicy {

  private final List<FailureClassificationRule> classificationRules;

  public DefaultFailurePolicy(List<FailureClassificationRule> classificationRules) {
    this.classificationRules =
        List.copyOf(Validate.notNull(classificationRules, "classificationRules must not be null."));
  }

  @Override
  public List<FailureClassificationRule> classificationRules() {
    return classificationRules;
  }
}
