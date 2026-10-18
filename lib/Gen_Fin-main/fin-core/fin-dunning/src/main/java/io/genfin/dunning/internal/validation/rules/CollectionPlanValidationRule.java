package io.genfin.dunning.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.dunning.collection.CollectionPlan;
import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.validation.ValidationContext;
import io.genfin.dunning.validation.ValidationIssue;
import io.genfin.dunning.validation.ValidationRule;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Structural sanity check for a {@link CollectionPlan}: its resolved stage list must not repeat the
 * same {@link CollectionStage} twice - an application configures any subset/order of stages, but
 * each stage a case can be in appears at most once in the template.
 */
public final class CollectionPlanValidationRule implements ValidationRule {

  private static final String RULE_CODE = "COLLECTION_PLAN";

  @Override
  public List<ValidationIssue> apply(ValidationContext context) {
    CollectionPlan plan = context.collectionPlan();
    if (plan == null) {
      return List.of();
    }

    List<ValidationIssue> issues = new ArrayList<>();
    EnumSet<CollectionStage> seen = EnumSet.noneOf(CollectionStage.class);
    for (CollectionStage stage : plan.stages()) {
      if (!seen.add(stage)) {
        issues.add(
            ValidationIssue.of(
                RULE_CODE, "duplicate stage " + stage + " in collection plan.", Severity.ERROR));
      }
    }

    return issues;
  }
}
