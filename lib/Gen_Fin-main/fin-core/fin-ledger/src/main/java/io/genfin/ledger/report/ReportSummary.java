package io.genfin.ledger.report;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;

/**
 * The grand-total roll-up shown alongside a report's {@link ReportSection}s: how many lines it
 * covers and the total of each side. A well-formed report always has {@code totalDebit} equal to
 * {@code totalCredit} - see {@link #isBalanced()}.
 */
public record ReportSummary(int lineCount, Money totalDebit, Money totalCredit)
    implements ValueObject {

  public ReportSummary {
    Validate.nonNegative(lineCount, "lineCount must not be negative.");
    Validate.notNull(totalDebit, "totalDebit must not be null.");
    Validate.notNull(totalCredit, "totalCredit must not be null.");
  }

  public boolean isBalanced() {
    return totalDebit.equals(totalCredit);
  }
}
