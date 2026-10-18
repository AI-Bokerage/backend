package io.genfin.reconciliation.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.matching.MatchCandidate;
import io.genfin.reconciliation.reconciliation.ReconciliationItem;
import io.genfin.refund.reference.Reference;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComparisonEngineTest {

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

  @Test
  void identicalRecordsHaveNoDifferences() {
    ComparisonRecord left = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    ComparisonRecord right = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));

    ComparisonResult result =
        ComparisonPolicies.standard().evaluate(left, right, ComparisonContext.strict());

    assertThat(result.isIdentical()).isTrue();
  }

  @Test
  void currencyMismatchIsCriticalAndSuppressesAmountComparison() {
    ComparisonRecord left = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    ComparisonRecord right = ComparisonRecord.of(eur("100.00"), Reference.payment("PAY-1"));

    ComparisonResult result =
        ComparisonPolicies.standard().evaluate(left, right, ComparisonContext.strict());

    assertThat(result.differences()).hasSize(1);
    assertThat(result.differences().get(0).type()).isEqualTo(DifferenceType.CURRENCY);
    assertThat(result.hasCriticalDifference()).isTrue();
  }

  @Test
  void amountGapWithinToleranceIsMinorBeyondItIsMajor() {
    ComparisonRecord left = ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"));
    ComparisonRecord withinTolerance =
        ComparisonRecord.of(usd("99.50"), Reference.payment("PAY-1"));
    ComparisonRecord outsideTolerance =
        ComparisonRecord.of(usd("90.00"), Reference.payment("PAY-1"));
    ComparisonContext context = ComparisonContext.of(usd("1.00"), Duration.ofDays(1));

    Difference minor = ComparisonStrategies.amount().compare(left, withinTolerance, context).get(0);
    Difference major =
        ComparisonStrategies.amount().compare(left, outsideTolerance, context).get(0);

    assertThat(minor.severity()).isEqualTo(DifferenceSeverity.MINOR);
    assertThat(major.severity()).isEqualTo(DifferenceSeverity.MAJOR);
  }

  @Test
  void statusComparisonIsCaseInsensitive() {
    ComparisonRecord left =
        ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1")).withStatus("CAPTURED");
    ComparisonRecord same =
        ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1")).withStatus("captured");
    ComparisonRecord different =
        ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1")).withStatus("failed");

    assertThat(ComparisonStrategies.status().compare(left, same, ComparisonContext.strict()))
        .isEmpty();
    assertThat(ComparisonStrategies.status().compare(left, different, ComparisonContext.strict()))
        .hasSize(1);
  }

  @Test
  void metadataAndAttributeDifferencesAreReportedPerKey() {
    ComparisonRecord left =
        ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"))
            .withMetadata(Map.of("orderId", "O-1"))
            .withAttributes(Map.of("channel", "web"));
    ComparisonRecord right =
        ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"))
            .withMetadata(Map.of("orderId", "O-2"))
            .withAttributes(Map.of("channel", "mobile"));

    ComparisonResult result =
        ComparisonPolicies.standard().evaluate(left, right, ComparisonContext.strict());

    assertThat(result.differences())
        .extracting(Difference::type)
        .containsExactlyInAnyOrder(DifferenceType.METADATA, DifferenceType.ATTRIBUTE);
  }

  @Test
  void timestampBeyondToleranceIsReported() {
    Instant now = Instant.parse("2026-07-31T00:00:00Z");
    ComparisonRecord left =
        ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1")).withTimestamp(now);
    ComparisonRecord tooFar =
        ComparisonRecord.of(usd("100.00"), Reference.payment("PAY-1"))
            .withTimestamp(now.plusSeconds(200_000));

    assertThat(ComparisonStrategies.timestamp().compare(left, tooFar, ComparisonContext.strict()))
        .hasSize(1);
  }

  @Test
  void matchCandidateAdaptsToComparisonRecordForUseFromMatching() {
    Instant now = Instant.parse("2026-07-31T00:00:00Z");
    MatchCandidate candidate =
        MatchCandidate.of(ReconciliationItem.of(Reference.payment("PAY-1"), usd("100.00")), now);

    ComparisonRecord record = candidate.toComparisonRecord();

    assertThat(record.amount()).isEqualTo(usd("100.00"));
    assertThat(record.reference()).isEqualTo(Reference.payment("PAY-1"));
    assertThat(record.timestamp()).isEqualTo(now);
  }

  private Money usd(String amount) {
    return Money.of(new BigDecimal(amount), USD);
  }

  private Money eur(String amount) {
    return Money.of(new BigDecimal(amount), EUR);
  }
}
