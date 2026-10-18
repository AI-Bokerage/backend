package io.genfin.ledger.trialbalance;

import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.validation.ValidationIssue;
import io.genfin.ledger.validation.ValidationResult;
import java.util.List;

/**
 * Verifies that total debits equal total credits across a whole {@link TrialBalance}. Plain
 * arithmetic over an already-computed {@link TrialBalanceSummary} has exactly one correct answer,
 * so - like {@link io.genfin.ledger.posting.PostingCalculator} - this is a static utility rather
 * than an SPI extension point.
 */
public final class TrialBalanceValidator {

  private TrialBalanceValidator() {}

  public static ValidationResult validate(TrialBalance trialBalance) {
    Validate.notNull(trialBalance, "trialBalance must not be null.");
    TrialBalanceSummary summary = trialBalance.summary();
    if (!summary.totalDebit().equals(summary.totalCredit())) {
      return new ValidationResult(
          List.of(
              ValidationIssue.of(
                  "TRIAL_BALANCE_UNBALANCED",
                  "total debits ("
                      + summary.totalDebit()
                      + ") must equal total credits ("
                      + summary.totalCredit()
                      + ").",
                  Severity.CRITICAL)));
    }
    return ValidationResult.valid();
  }
}
