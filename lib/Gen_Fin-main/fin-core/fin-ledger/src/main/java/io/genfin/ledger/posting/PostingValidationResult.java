package io.genfin.ledger.posting;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.Severity;
import io.genfin.api.util.CollectionUtils;
import java.util.List;

/**
 * The outcome of {@link io.genfin.ledger.port.posting.PostingValidator#validate}. Mirrors {@code
 * io.genfin.refund.validation.ValidationResult}: valid whenever no issue is {@link Severity#ERROR}
 * or {@link Severity#CRITICAL}.
 */
public record PostingValidationResult(List<PostingIssue> issues) implements ValueObject {

  public PostingValidationResult {
    issues = CollectionUtils.immutableList(issues);
  }

  public static PostingValidationResult valid() {
    return new PostingValidationResult(List.of());
  }

  public boolean isValid() {
    return issues.stream()
        .noneMatch(
            issue -> issue.severity() == Severity.ERROR || issue.severity() == Severity.CRITICAL);
  }
}
