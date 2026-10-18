package io.genfin.ledger.period;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.exception.Severity;
import io.genfin.api.util.CollectionUtils;
import java.util.List;

/**
 * The outcome of a {@link PeriodValidator} check. Mirrors {@code
 * io.genfin.ledger.posting.PostingValidationResult}: valid whenever no issue is {@link
 * Severity#ERROR} or {@link Severity#CRITICAL}.
 */
public record PeriodValidationResult(List<PeriodIssue> issues) implements ValueObject {

  public PeriodValidationResult {
    issues = CollectionUtils.immutableList(issues);
  }

  public static PeriodValidationResult valid() {
    return new PeriodValidationResult(List.of());
  }

  public boolean isValid() {
    return issues.stream()
        .noneMatch(
            issue -> issue.severity() == Severity.ERROR || issue.severity() == Severity.CRITICAL);
  }
}
