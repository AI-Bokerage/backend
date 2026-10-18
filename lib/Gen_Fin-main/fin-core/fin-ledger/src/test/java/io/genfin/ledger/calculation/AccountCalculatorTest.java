package io.genfin.ledger.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.ledger.account.Account;
import io.genfin.ledger.account.AccountAttributes;
import io.genfin.ledger.account.AccountClassification;
import io.genfin.ledger.account.AccountMetadata;
import io.genfin.ledger.account.AccountType;
import io.genfin.ledger.account.ChartOfAccounts;
import io.genfin.ledger.balance.Balance;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.ChartOfAccountsId;
import io.genfin.ledger.port.calculation.AccountCalculator;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AccountCalculatorTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  private static final AccountType CASH_TYPE = accountType("CASH", AccountClassification.ASSET);
  private static final AccountType REVENUE_TYPE =
      accountType("REVENUE", AccountClassification.REVENUE);

  private static AccountType accountType(String code, AccountClassification classification) {
    return new AccountType() {
      @Override
      public String code() {
        return code;
      }

      @Override
      public AccountClassification classification() {
        return classification;
      }
    };
  }

  private static Account account(ChartOfAccountsId chartId, String code, AccountType type) {
    return new Account(
        AccountId.generate(),
        chartId,
        code,
        code,
        type,
        null,
        null,
        AccountAttributes.standard(),
        AccountMetadata.empty());
  }

  @Test
  void sumsNetAmountsForAccountsInTheGivenClassification() {
    ChartOfAccounts chart = ChartOfAccounts.open();
    Account cash = account(chart.id(), "1000", CASH_TYPE);
    Account revenue = account(chart.id(), "4000", REVENUE_TYPE);
    chart.add(cash);
    chart.add(revenue);
    List<Balance> balances =
        List.of(
            new Balance(cash.id(), Money.of("100.00", USD), Money.of("40.00", USD)),
            new Balance(revenue.id(), Money.of("0.00", USD), Money.of("60.00", USD)));

    AccountCalculator calculator = AccountCalculators.standard();

    Money assetTotal = calculator.totalFor(AccountClassification.ASSET, chart, balances, USD);
    Map<AccountClassification, Money> byClassification =
        calculator.totalsByClassification(chart, balances, USD);

    assertThat(assetTotal).isEqualTo(Money.of("60.00", USD));
    assertThat(byClassification.get(AccountClassification.ASSET)).isEqualTo(Money.of("60.00", USD));
    assertThat(byClassification.get(AccountClassification.REVENUE))
        .isEqualTo(Money.of("60.00", USD));
    assertThat(byClassification.get(AccountClassification.EQUITY)).isEqualTo(Money.zero(USD));
  }

  @Test
  void ignoresBalancesForAccountsNoLongerInTheChart() {
    ChartOfAccounts chart = ChartOfAccounts.open();
    List<Balance> balances =
        List.of(new Balance(AccountId.generate(), Money.of("50.00", USD), Money.zero(USD)));

    AccountCalculator calculator = AccountCalculators.standard();

    Money total = calculator.totalFor(AccountClassification.ASSET, chart, balances, USD);

    assertThat(total).isEqualTo(Money.zero(USD));
  }
}
