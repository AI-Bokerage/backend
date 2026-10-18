package io.genfin.reconciliation.validation;

import io.genfin.reconciliation.reconciliation.Reconciliation;
import java.util.List;

/**
 * One composable check. A {@link Validators}-built validator runs every rule and never
 * short-circuits, so a single validation pass surfaces every issue at once.
 */
@FunctionalInterface
public interface ValidationRule {

  List<ValidationIssue> apply(Reconciliation reconciliation, ValidationContext context);
}
