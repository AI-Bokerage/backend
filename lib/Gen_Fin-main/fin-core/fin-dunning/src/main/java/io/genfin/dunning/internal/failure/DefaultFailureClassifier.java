package io.genfin.dunning.internal.failure;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.failure.FailureClassification;
import io.genfin.dunning.failure.FailureClassificationRule;
import io.genfin.dunning.failure.FailureReason;
import io.genfin.dunning.port.failure.FailureClassifier;
import io.genfin.dunning.port.failure.FailurePolicy;
import java.util.Optional;

/**
 * Looks up the first {@link FailureClassificationRule} in the policy's scheme whose category
 * matches the reason's category. No matching rule (an empty scheme, or a category the scheme
 * doesn't cover) decides {@link FailureClassification#unclassified(FailureReason)}. Never performs
 * any disposition itself.
 */
public final class DefaultFailureClassifier implements FailureClassifier {

  @Override
  public FailureClassification classify(FailureReason reason, FailurePolicy policy) {
    Validate.notNull(reason, "reason must not be null.");
    Validate.notNull(policy, "policy must not be null.");

    Optional<FailureClassificationRule> matched =
        policy.classificationRules().stream()
            .filter(rule -> rule.category().code().equals(reason.category().code()))
            .findFirst();

    return matched
        .map(rule -> FailureClassification.classified(reason, rule.disposition()))
        .orElseGet(() -> FailureClassification.unclassified(reason));
  }
}
