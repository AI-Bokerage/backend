package io.genfin.ledger.balance;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.util.CollectionUtils;
import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import java.util.List;

/**
 * The ledger-wide roll-up of a set of {@link Balance}s: every account's totals alongside the grand
 * total debit and credit across all of them. A trial balance is "in balance" exactly when {@link
 * #totalDebit()} equals {@link #totalCredit()}, which double-entry posting already guarantees by
 * construction for any set of fully-posted entries.
 */
public record BalanceSummary(List<Balance> balances, Money totalDebit, Money totalCredit)
    implements ValueObject {

  public BalanceSummary {
    balances = CollectionUtils.immutableList(balances);
    Validate.notNull(totalDebit, "totalDebit must not be null.");
    Validate.notNull(totalCredit, "totalCredit must not be null.");
  }

  /** Sums {@code balances} into their grand total debit and credit. */
  public static BalanceSummary of(List<Balance> balances, Currency currency) {
    Validate.notNull(balances, "balances must not be null.");
    Money totalDebit = Money.zero(currency);
    Money totalCredit = Money.zero(currency);
    for (Balance balance : balances) {
      totalDebit = totalDebit.add(balance.totalDebit());
      totalCredit = totalCredit.add(balance.totalCredit());
    }
    return new BalanceSummary(balances, totalDebit, totalCredit);
  }

  public boolean isBalanced() {
    return totalDebit.equals(totalCredit);
  }
}
