package io.genfin.ledger.posting;

import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountId;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure arithmetic over a set of {@link PostingEntry}s - summing debits/credits and rolling them up
 * per account. Plain summation has exactly one correct implementation, so unlike the SPI ports in
 * this package, this is a static utility rather than an extension point, mirroring {@code
 * io.genfin.api.util.CollectionUtils}.
 */
public final class PostingCalculator {

  private PostingCalculator() {}

  /** The total of every {@link PostingEntry#isDebit()} amount in {@code entries}. */
  public static Money totalDebit(List<PostingEntry> entries, Currency currency) {
    return sum(entries, true, currency);
  }

  /** The total of every credit-side amount in {@code entries}. */
  public static Money totalCredit(List<PostingEntry> entries, Currency currency) {
    return sum(entries, false, currency);
  }

  /** Whether {@code entries} sum to equal total debits and total credits. */
  public static boolean isBalanced(List<PostingEntry> entries, Currency currency) {
    return totalDebit(entries, currency).equals(totalCredit(entries, currency));
  }

  /** Debit/credit totals per {@link AccountId} touched by {@code entries}, in first-seen order. */
  public static List<PostingBalance> balances(List<PostingEntry> entries, Currency currency) {
    Validate.notNull(entries, "entries must not be null.");
    Map<AccountId, Money[]> totals = new LinkedHashMap<>();
    for (PostingEntry entry : CollectionUtils.immutableList(entries)) {
      Money[] pair =
          totals.computeIfAbsent(
              entry.accountId(), id -> new Money[] {Money.zero(currency), Money.zero(currency)});
      if (entry.isDebit()) {
        pair[0] = pair[0].add(entry.amount());
      } else {
        pair[1] = pair[1].add(entry.amount());
      }
    }
    return totals.entrySet().stream()
        .map(e -> new PostingBalance(e.getKey(), e.getValue()[0], e.getValue()[1]))
        .toList();
  }

  private static Money sum(List<PostingEntry> entries, boolean debit, Currency currency) {
    Validate.notNull(entries, "entries must not be null.");
    Money total = Money.zero(currency);
    for (PostingEntry entry : CollectionUtils.immutableList(entries)) {
      if (entry.isDebit() == debit) {
        total = total.add(entry.amount());
      }
    }
    return total;
  }
}
