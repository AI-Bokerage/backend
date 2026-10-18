package io.genfin.dunning.failure;

import java.util.List;

/**
 * Immutable, builder-based configuration for a {@code FailurePolicy}. The empty default rule list
 * below exists only so {@code FailurePolicyConfiguration.builder().build()} compiles and is
 * testable out of the box (an obligation with no rules simply classifies to {@code
 * FailureClassification#unclassified}) - resolving the real classification scheme for a deployment
 * always flows through an application's own {@code DunningPolicy}, never a literal baked in here.
 */
public final class FailurePolicyConfiguration {

  private final List<FailureClassificationRule> classificationRules;

  private FailurePolicyConfiguration(Builder builder) {
    this.classificationRules = List.copyOf(builder.classificationRules);
  }

  public static Builder builder() {
    return new Builder();
  }

  public List<FailureClassificationRule> classificationRules() {
    return classificationRules;
  }

  public static final class Builder {

    private List<FailureClassificationRule> classificationRules = List.of();

    public Builder classificationRules(List<FailureClassificationRule> classificationRules) {
      this.classificationRules = classificationRules;
      return this;
    }

    public FailurePolicyConfiguration build() {
      return new FailurePolicyConfiguration(this);
    }
  }
}
