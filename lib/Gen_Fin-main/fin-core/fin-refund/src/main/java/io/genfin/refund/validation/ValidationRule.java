package io.genfin.refund.validation;

import io.genfin.refund.refund.Refund;
import java.util.List;

@FunctionalInterface
public interface ValidationRule {

  List<ValidationIssue> apply(Refund refund, ValidationContext context);
}
