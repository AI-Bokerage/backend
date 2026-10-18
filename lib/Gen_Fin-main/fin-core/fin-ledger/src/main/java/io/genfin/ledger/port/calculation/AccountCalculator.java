package io.genfin.ledger.port.calculation;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.account.AccountClassification;
import io.genfin.ledger.account.ChartOfAccounts;
import io.genfin.ledger.balance.Balance;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import java.util.List;
import java.util.Map;

/**
 * Rolls up a set of per-account {@link Balance}s by {@link AccountClassification} - e.g. every
 * ASSET account's net position summed into one figure for a {@link
 * io.genfin.ledger.report.FinancialStatementModel} section. Looks up each account's classification
 * in the supplied {@link ChartOfAccounts} rather than deciding it itself; balances for an account
 * no longer in the chart are ignored.
 */
public interface AccountCalculator extends Extension {

  Money totalFor(
      AccountClassification classification,
      ChartOfAccounts chart,
      List<Balance> balances,
      Currency currency);

  Map<AccountClassification, Money> totalsByClassification(
      ChartOfAccounts chart, List<Balance> balances, Currency currency);
}
