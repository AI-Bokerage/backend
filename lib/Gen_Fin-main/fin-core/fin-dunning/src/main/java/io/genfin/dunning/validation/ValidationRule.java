package io.genfin.dunning.validation;

import java.util.List;

/**
 * One composable check over a {@link ValidationContext}. A {@link
 * io.genfin.dunning.port.validation.DunningValidator} runs every registered rule and never
 * short-circuits, so a single validation pass surfaces every issue at once. A rule ignores
 * whichever of {@link ValidationContext}'s fields it does not care about, and reports no issue when
 * the field it does care about is absent.
 */
@FunctionalInterface
public interface ValidationRule {

  List<ValidationIssue> apply(ValidationContext context);
}
