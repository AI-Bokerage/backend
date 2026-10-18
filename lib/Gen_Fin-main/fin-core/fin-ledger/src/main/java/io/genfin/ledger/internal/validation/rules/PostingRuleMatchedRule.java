package io.genfin.ledger.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.validation.ValidationContext;
import io.genfin.ledger.validation.ValidationIssue;
import io.genfin.ledger.validation.ValidationRule;
import java.util.List;

/**
 * A {@link JournalEntry} must have been produced by a registered {@link
 * io.genfin.ledger.port.posting.PostingRule} - i.e. its originating {@link
 * io.genfin.ledger.fact.FinancialFact} resolved to a {@link
 * io.genfin.ledger.posting.PostingRuleSet} an application registered, not an ad hoc set of lines
 * nobody configured. Whoever resolved the fact records the outcome on {@link
 * ValidationContext#postingRuleMatched()}; this rule reports no issue when the context leaves that
 * unset - it has nothing to check.
 */
public final class PostingRuleMatchedRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(JournalEntry entry, ValidationContext context) {
    Boolean matched = context.postingRuleMatched();
    if (matched == null) {
      return List.of();
    }
    if (!matched) {
      return List.of(
          ValidationIssue.of(
              "POSTING_RULE_NOT_MATCHED",
              "journal entry did not match a registered PostingRule.",
              Severity.ERROR));
    }
    return List.of();
  }
}
