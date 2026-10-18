package io.genfin.ledger.internal.posting;

import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.port.posting.PostingRule;
import io.genfin.ledger.port.posting.PostingValidator;
import io.genfin.ledger.posting.PostingCalculator;
import io.genfin.ledger.posting.PostingContext;
import io.genfin.ledger.posting.PostingEntry;
import io.genfin.ledger.posting.PostingIssue;
import io.genfin.ledger.posting.PostingValidationResult;
import io.genfin.money.currency.Currency;
import io.genfin.money.exception.CurrencyMismatchException;
import io.genfin.money.money.Money;
import java.util.ArrayList;
import java.util.List;

/**
 * Enforces the hard double-entry invariant itself - at least two legs, total debit equal to total
 * credit, all in one currency - unconditionally and before running any configured {@link
 * PostingRule}. No rule or configuration can weaken this check; it is not exposed as a {@link
 * PostingRule} for exactly that reason. Never short-circuits: every issue found, from the invariant
 * and from every rule, is collected, mirroring {@code
 * io.genfin.refund.internal.validation.DefaultRefundValidator}.
 */
public final class DefaultPostingValidator implements PostingValidator {

  private static final int MINIMUM_ENTRIES = 2;

  private final List<PostingRule> rules;

  public DefaultPostingValidator(List<PostingRule> rules) {
    this.rules = List.copyOf(rules);
  }

  @Override
  public PostingValidationResult validate(List<PostingEntry> entries, PostingContext context) {
    Validate.notNull(entries, "entries must not be null.");
    Validate.notNull(context, "context must not be null.");
    List<PostingIssue> issues = new ArrayList<>(checkBalance(entries));
    for (PostingRule rule : rules) {
      issues.addAll(rule.evaluate(entries, context));
    }
    return new PostingValidationResult(issues);
  }

  private List<PostingIssue> checkBalance(List<PostingEntry> entries) {
    if (entries.size() < MINIMUM_ENTRIES) {
      return List.of(
          PostingIssue.of(
              "INSUFFICIENT_POSTING_LINES",
              "a posting needs at least two entries, found " + entries.size() + ".",
              Severity.CRITICAL));
    }
    Currency currency = entries.get(0).amount().currency();
    Money totalDebit;
    Money totalCredit;
    try {
      totalDebit = PostingCalculator.totalDebit(entries, currency);
      totalCredit = PostingCalculator.totalCredit(entries, currency);
    } catch (CurrencyMismatchException e) {
      return List.of(
          PostingIssue.of(
              "MIXED_CURRENCY_POSTING",
              "a posting must use one currency: " + e.getMessage(),
              Severity.CRITICAL));
    }
    if (!totalDebit.equals(totalCredit)) {
      return List.of(
          PostingIssue.of(
              "UNBALANCED_POSTING",
              "unbalanced posting: total debit ("
                  + totalDebit
                  + ") != total credit ("
                  + totalCredit
                  + ").",
              Severity.CRITICAL));
    }
    return List.of();
  }
}
