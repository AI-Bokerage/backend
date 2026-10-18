package io.genfin.ledger.internal.validation.rules;

import io.genfin.api.exception.Severity;
import io.genfin.ledger.account.Account;
import io.genfin.ledger.account.ChartOfAccounts;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.validation.ValidationContext;
import io.genfin.ledger.validation.ValidationIssue;
import io.genfin.ledger.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;

/**
 * Every {@link AccountId} a {@link JournalEntry} posts against must exist, and be open for posting,
 * in the {@link ChartOfAccounts} supplied on the {@link ValidationContext}. Reports no issue when
 * the context carries no chart - the rule has nothing to check it against.
 */
public final class AccountExistsRule implements ValidationRule {

  @Override
  public List<ValidationIssue> apply(JournalEntry entry, ValidationContext context) {
    ChartOfAccounts chartOfAccounts = context.chartOfAccounts();
    if (chartOfAccounts == null) {
      return List.of();
    }
    List<ValidationIssue> issues = new ArrayList<>();
    for (AccountId accountId : distinctAccountIds(entry)) {
      chartOfAccounts
          .find(accountId)
          .ifPresentOrElse(
              account -> checkPostable(account, issues), () -> reportMissing(accountId, issues));
    }
    return issues;
  }

  private static List<AccountId> distinctAccountIds(JournalEntry entry) {
    return entry.lines().stream().map(line -> line.accountId()).distinct().toList();
  }

  private void checkPostable(Account account, List<ValidationIssue> issues) {
    if (!account.isPostingAllowed()) {
      issues.add(
          ValidationIssue.of(
              "ACCOUNT_NOT_POSTABLE",
              "account '" + account.code() + "' does not accept postings.",
              Severity.ERROR));
    }
  }

  private void reportMissing(AccountId accountId, List<ValidationIssue> issues) {
    issues.add(
        ValidationIssue.of(
            "ACCOUNT_NOT_FOUND",
            "account " + accountId.value() + " does not exist in the chart of accounts.",
            Severity.ERROR));
  }
}
