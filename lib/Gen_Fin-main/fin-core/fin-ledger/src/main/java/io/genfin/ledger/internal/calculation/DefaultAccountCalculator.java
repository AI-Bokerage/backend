package io.genfin.ledger.internal.calculation;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.account.Account;
import io.genfin.ledger.account.AccountClassification;
import io.genfin.ledger.account.ChartOfAccounts;
import io.genfin.ledger.balance.Balance;
import io.genfin.ledger.port.calculation.AccountCalculator;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The standard {@link AccountCalculator}: sums each {@link
 * Balance#netAmount(AccountClassification)} into the classification the {@link ChartOfAccounts}
 * records for its account.
 */
public final class DefaultAccountCalculator implements AccountCalculator {

  @Override
  public Money totalFor(
      AccountClassification classification,
      ChartOfAccounts chart,
      List<Balance> balances,
      Currency currency) {
    Validate.notNull(classification, "classification must not be null.");
    Validate.notNull(chart, "chart must not be null.");
    Validate.notNull(balances, "balances must not be null.");
    Validate.notNull(currency, "currency must not be null.");
    Money total = Money.zero(currency);
    for (Balance balance : balances) {
      Optional<Account> account = chart.find(balance.accountId());
      if (account.isPresent() && account.get().classification() == classification) {
        total = total.add(balance.netAmount(classification));
      }
    }
    return total;
  }

  @Override
  public Map<AccountClassification, Money> totalsByClassification(
      ChartOfAccounts chart, List<Balance> balances, Currency currency) {
    Validate.notNull(chart, "chart must not be null.");
    Validate.notNull(balances, "balances must not be null.");
    Validate.notNull(currency, "currency must not be null.");
    Map<AccountClassification, Money> totals = new EnumMap<>(AccountClassification.class);
    for (AccountClassification classification : AccountClassification.values()) {
      totals.put(classification, totalFor(classification, chart, balances, currency));
    }
    return totals;
  }
}
