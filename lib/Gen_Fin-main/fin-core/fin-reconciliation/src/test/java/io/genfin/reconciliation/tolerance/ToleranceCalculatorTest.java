package io.genfin.reconciliation.tolerance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.port.tolerance.CustomToleranceRule;
import io.genfin.reconciliation.port.tolerance.CustomToleranceRuleRegistry;
import io.genfin.reconciliation.port.tolerance.ToleranceCalculator;
import io.genfin.refund.reference.Reference;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToleranceCalculatorTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency()
          .code("USD")
          .symbol("$")
          .displayName("US Dollar")
          .fractionDigits(2)
          .build();

  private static final Currency USD_SETTLEMENT =
      CurrencyFactory.newCurrency()
          .code("USDX")
          .symbol("$")
          .displayName("US Dollar Settlement")
          .fractionDigits(2)
          .build();

  private final ToleranceCalculator calculator = ToleranceCalculators.standard();

  @Test
  void amountWithinToleranceIsAMatchWithDegradedConfidence() {
    ComparisonRecord left = record("100.00");
    ComparisonRecord right = record("100.50");

    ToleranceResult result =
        calculator.evaluate(AmountTolerance.of(Money.of(new BigDecimal("1.00"), USD)), left, right);

    assertThat(result.isWithinTolerance()).isTrue();
    assertThat(result.confidence()).isBetween(0.0, 1.0).isLessThan(1.0);
  }

  @Test
  void amountBeyondToleranceExceeds() {
    ToleranceResult result =
        calculator.evaluate(
            AmountTolerance.of(Money.of(new BigDecimal("1.00"), USD)),
            record("100.00"),
            record("105.00"));

    assertThat(result.outcome()).isEqualTo(ToleranceOutcome.EXCEEDS_TOLERANCE);
  }

  @Test
  void amountToleranceIsNotApplicableAcrossCurrencies() {
    ComparisonRecord left = record("100.00", USD);
    ComparisonRecord right = record("100.00", USD_SETTLEMENT);

    ToleranceResult result =
        calculator.evaluate(AmountTolerance.of(Money.of(new BigDecimal("1.00"), USD)), left, right);

    assertThat(result.outcome()).isEqualTo(ToleranceOutcome.NOT_APPLICABLE);
  }

  @Test
  void dateWithinToleranceIsAMatch() {
    Instant now = Instant.now();
    ComparisonRecord left = record("100.00").withTimestamp(now);
    ComparisonRecord right = record("100.00").withTimestamp(now.plus(Duration.ofHours(2)));

    ToleranceResult result =
        calculator.evaluate(DateTolerance.of(Duration.ofHours(6)), left, right);

    assertThat(result.isWithinTolerance()).isTrue();
  }

  @Test
  void percentageToleranceScalesWithTheReferenceAmount() {
    ComparisonRecord left = record("1000.00");
    ComparisonRecord right = record("1015.00");

    ToleranceResult result =
        calculator.evaluate(PercentageTolerance.of(new BigDecimal("0.02")), left, right);

    assertThat(result.isWithinTolerance()).isTrue();
  }

  @Test
  void percentageToleranceExceededBeyondTheFraction() {
    ComparisonRecord left = record("1000.00");
    ComparisonRecord right = record("1050.00");

    ToleranceResult result =
        calculator.evaluate(PercentageTolerance.of(new BigDecimal("0.02")), left, right);

    assertThat(result.outcome()).isEqualTo(ToleranceOutcome.EXCEEDS_TOLERANCE);
  }

  @Test
  void currencyToleranceAcceptsConfiguredEquivalentCurrencies() {
    ComparisonRecord left = record("100.00", USD);
    ComparisonRecord right = record("100.00", USD_SETTLEMENT);

    ToleranceResult result =
        calculator.evaluate(CurrencyTolerance.of(Set.of(USD, USD_SETTLEMENT)), left, right);

    assertThat(result.isWithinTolerance()).isTrue();
  }

  @Test
  void customToleranceDelegatesToTheRegisteredRule() {
    CustomToleranceRuleRegistry registry = ToleranceCalculators.newCustomRuleRegistry();
    registry.register(
        new CustomToleranceRule() {
          @Override
          public String name() {
            return "always-within";
          }

          @Override
          public ToleranceResult evaluate(ComparisonRecord left, ComparisonRecord right) {
            return ToleranceResult.within(1.0, "always within for this test.");
          }
        });
    ToleranceCalculator customCalculator = ToleranceCalculators.of(registry);

    ToleranceResult result =
        customCalculator.evaluate(
            CustomTolerance.of("always-within"), record("1.00"), record("999.00"));

    assertThat(result.isWithinTolerance()).isTrue();
  }

  @Test
  void unregisteredCustomToleranceRuleFailsFast() {
    ToleranceCalculator standalone = ToleranceCalculators.standard();

    assertThatThrownBy(
            () ->
                standalone.evaluate(CustomTolerance.of("missing"), record("1.00"), record("1.00")))
        .isInstanceOf(RuntimeException.class);
  }

  private ComparisonRecord record(String amount) {
    return record(amount, USD);
  }

  private ComparisonRecord record(String amount, Currency currency) {
    return ComparisonRecord.of(
        Money.of(new BigDecimal(amount), currency), Reference.payment("PAY-1"));
  }
}
