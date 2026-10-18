package io.genfin.reconciliation.rule;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.rule.RuleEngine;
import io.genfin.reconciliation.tolerance.AmountTolerance;
import io.genfin.reconciliation.tolerance.ToleranceCalculators;
import io.genfin.refund.reference.Reference;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency()
          .code("USD")
          .symbol("$")
          .displayName("US Dollar")
          .fractionDigits(2)
          .build();

  private static final Currency EUR =
      CurrencyFactory.newCurrency()
          .code("EUR")
          .symbol("€")
          .displayName("Euro")
          .fractionDigits(2)
          .build();

  private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

  private final RuleEngine engine = RuleEngines.standard();

  @Test
  void identicalRecordsRaiseNoResults() {
    ComparisonRecord left = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    ComparisonRecord right = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));

    assertThat(engine.evaluate(left, right, RuleContext.at(NOW))).isEmpty();
  }

  @Test
  void amountMismatchIsReported() {
    ComparisonRecord left = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    ComparisonRecord right = ComparisonRecord.of(usd("90.00"), Reference.payment("PAY-1"));

    assertThat(engine.evaluate(left, right, RuleContext.at(NOW)))
        .extracting(RuleResult::ruleCode)
        .contains("AMOUNTS_EQUAL");
  }

  @Test
  void amountGapWithinConfiguredToleranceRaisesNoToleranceViolation() {
    ComparisonRecord left = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    ComparisonRecord right = ComparisonRecord.of(usd("99.50"), Reference.payment("PAY-1"));
    RuleContext context =
        RuleContext.at(NOW)
            .withAmountTolerance(AmountTolerance.of(usd("1.00")), ToleranceCalculators.standard());

    assertThat(engine.evaluate(left, right, context))
        .extracting(RuleResult::ruleCode)
        .doesNotContain("AMOUNTS_WITHIN_TOLERANCE");
  }

  @Test
  void amountGapBeyondConfiguredToleranceIsReported() {
    ComparisonRecord left = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    ComparisonRecord right = ComparisonRecord.of(usd("90.00"), Reference.payment("PAY-1"));
    RuleContext context =
        RuleContext.at(NOW)
            .withAmountTolerance(AmountTolerance.of(usd("1.00")), ToleranceCalculators.standard());

    assertThat(engine.evaluate(left, right, context))
        .extracting(RuleResult::ruleCode)
        .contains("AMOUNTS_WITHIN_TOLERANCE");
  }

  @Test
  void currencyMismatchIsReportedAndSuppressesAmountsEqual() {
    ComparisonRecord left = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    ComparisonRecord right = ComparisonRecord.of(eur("100.00"), Reference.payment("PAY-1"));

    assertThat(engine.evaluate(left, right, RuleContext.at(NOW)))
        .extracting(RuleResult::ruleCode)
        .containsExactly("CURRENCIES_EQUAL");
  }

  @Test
  void referenceMismatchIsReported() {
    ComparisonRecord left = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    ComparisonRecord right = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-2"));

    assertThat(engine.evaluate(left, right, RuleContext.at(NOW)))
        .extracting(RuleResult::ruleCode)
        .containsExactly("REFERENCES_EQUAL");
  }

  @Test
  void unresolvedContextFlagsRaiseNothing() {
    ComparisonRecord left = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    ComparisonRecord right = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));

    assertThat(engine.evaluate(left, right, RuleContext.at(NOW))).isEmpty();
  }

  @Test
  void resolvedContextFlagsEachRaiseTheirOwnRule() {
    ComparisonRecord left = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    ComparisonRecord right = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    RuleContext context =
        RuleContext.at(NOW)
            .withRefundExists(false)
            .withDuplicatePayment(true)
            .withDuplicateRefund(true)
            .withSettlementExists(false)
            .withPaymentExists(false)
            .withExpired(true);

    assertThat(engine.evaluate(left, right, context))
        .extracting(RuleResult::ruleCode)
        .containsExactlyInAnyOrder(
            "REFUND_EXISTS",
            "DUPLICATE_PAYMENT",
            "DUPLICATE_REFUND",
            "MISSING_SETTLEMENT",
            "MISSING_PAYMENT",
            "EXPIRED_TRANSACTION");
  }

  private Money usd(String amount) {
    return Money.of(new BigDecimal(amount), USD);
  }

  private Money eur(String amount) {
    return Money.of(new BigDecimal(amount), EUR);
  }
}
