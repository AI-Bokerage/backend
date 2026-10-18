package io.genfin.dunning.failure;

import io.genfin.dunning.internal.failure.DefaultFailurePolicy;
import io.genfin.dunning.port.failure.FailurePolicy;
import java.util.List;

/** Factory for {@link FailurePolicy} instances. */
public final class FailurePolicies {

  private FailurePolicies() {}

  public static FailurePolicy of(FailurePolicyConfiguration configuration) {
    return new DefaultFailurePolicy(configuration.classificationRules());
  }

  public static FailurePolicy of(List<FailureClassificationRule> classificationRules) {
    return new DefaultFailurePolicy(classificationRules);
  }

  /**
   * A policy built from a fully-defaulted example configuration - see {@link
   * FailurePolicyConfiguration.Builder}. Carries no rules, so every classification resolves to
   * unclassified until an application supplies its own scheme.
   */
  public static FailurePolicy standard() {
    return of(FailurePolicyConfiguration.builder().build());
  }
}
