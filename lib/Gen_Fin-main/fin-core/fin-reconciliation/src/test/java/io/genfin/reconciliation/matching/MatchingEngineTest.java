package io.genfin.reconciliation.matching;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.reconciliation.ReconciliationItem;
import io.genfin.refund.reference.Reference;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchingEngineTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency()
          .code("USD")
          .symbol("$")
          .displayName("US Dollar")
          .fractionDigits(2)
          .build();

  @Test
  void exactMatchAgreesOnlyWhenAmountAndReferenceAreIdentical() {
    MatchCandidate left = candidate("100.00", Reference.payment("PAY-1"));
    MatchCandidate right = candidate("100.00", Reference.payment("PAY-1"));
    MatchCandidate mismatched = candidate("100.00", Reference.payment("PAY-2"));

    MatchResult matched = MatchingStrategies.exact().match(left, right, MatchingContext.strict());
    MatchResult unmatched =
        MatchingStrategies.exact().match(left, mismatched, MatchingContext.strict());

    assertThat(matched.outcome()).isEqualTo(MatchOutcome.MATCHED);
    assertThat(unmatched.outcome()).isEqualTo(MatchOutcome.NOT_MATCHED);
  }

  @Test
  void toleranceMatchAcceptsSmallVarianceButRejectsLargeOne() {
    MatchCandidate left = candidate("100.00", Reference.payment("PAY-1"));
    MatchCandidate withinTolerance = candidate("99.50", Reference.payment("PAY-1"));
    MatchCandidate outsideTolerance = candidate("90.00", Reference.payment("PAY-1"));
    MatchingContext context = MatchingContext.of(Money.of(new BigDecimal("1.00"), USD));

    MatchResult accepted = MatchingStrategies.tolerance().match(left, withinTolerance, context);
    MatchResult rejected = MatchingStrategies.tolerance().match(left, outsideTolerance, context);

    assertThat(accepted.outcome()).isEqualTo(MatchOutcome.PARTIALLY_MATCHED);
    assertThat(accepted.varianceAmount()).isPresent();
    assertThat(rejected.outcome()).isEqualTo(MatchOutcome.NOT_MATCHED);
  }

  @Test
  void partialMatchCapturesShortfallWhenSettlementIsLessThanExpected() {
    MatchCandidate expected = candidate("100.00", Reference.payment("PAY-1"));
    MatchCandidate settled = candidate("60.00", Reference.payment("PAY-1"));

    MatchResult result =
        MatchingStrategies.partial().match(expected, settled, MatchingContext.strict());

    assertThat(result.outcome()).isEqualTo(MatchOutcome.PARTIALLY_MATCHED);
    assertThat(result.confidence()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.001));
    assertThat(result.varianceAmount()).contains(Money.of(new BigDecimal("40.00"), USD));
  }

  @Test
  void fuzzyMatchToleratesPunctuationAndCaseDifferences() {
    MatchCandidate left = candidate("100.00", Reference.payment("INV-1002"));
    MatchCandidate right = candidate("100.00", Reference.payment("inv 1002"));

    MatchResult result = MatchingStrategies.fuzzy().match(left, right, MatchingContext.strict());

    assertThat(result.outcome()).isEqualTo(MatchOutcome.MATCHED);
  }

  @Test
  void dateMatchRequiresBothTimestampsWithinTolerance() {
    Instant now = Instant.parse("2026-07-31T00:00:00Z");
    MatchCandidate left = candidate("100.00", Reference.payment("PAY-1"), now);
    MatchCandidate closeEnough =
        candidate("100.00", Reference.payment("PAY-1"), now.plusSeconds(3600));
    MatchCandidate tooFar =
        candidate("100.00", Reference.payment("PAY-1"), now.plusSeconds(200_000));
    MatchCandidate noTimestamp = candidate("100.00", Reference.payment("PAY-1"));

    assertThat(
            MatchingStrategies.date().match(left, closeEnough, MatchingContext.strict()).outcome())
        .isEqualTo(MatchOutcome.MATCHED);
    assertThat(MatchingStrategies.date().match(left, tooFar, MatchingContext.strict()).outcome())
        .isEqualTo(MatchOutcome.NOT_MATCHED);
    assertThat(
            MatchingStrategies.date().match(left, noTimestamp, MatchingContext.strict()).outcome())
        .isEqualTo(MatchOutcome.NOT_MATCHED);
  }

  @Test
  void amountMatchIgnoresReferenceAndComparesOnlyMoney() {
    MatchCandidate left = candidate("100.00", Reference.payment("PAY-1"));
    MatchCandidate sameAmountDifferentReference = candidate("100.00", Reference.payment("PAY-2"));
    MatchCandidate differentAmount = candidate("50.00", Reference.payment("PAY-1"));

    assertThat(
            MatchingStrategies.amount()
                .match(left, sameAmountDifferentReference, MatchingContext.strict())
                .outcome())
        .isEqualTo(MatchOutcome.MATCHED);
    assertThat(
            MatchingStrategies.amount()
                .match(left, differentAmount, MatchingContext.strict())
                .outcome())
        .isEqualTo(MatchOutcome.NOT_MATCHED);
  }

  @Test
  void referenceMatchIgnoresAmountAndComparesOnlyReference() {
    MatchCandidate left = candidate("100.00", Reference.payment("PAY-1"));
    MatchCandidate sameReferenceDifferentAmount = candidate("999.00", Reference.payment("PAY-1"));
    MatchCandidate differentReference = candidate("100.00", Reference.payment("PAY-2"));

    assertThat(
            MatchingStrategies.reference()
                .match(left, sameReferenceDifferentAmount, MatchingContext.strict())
                .outcome())
        .isEqualTo(MatchOutcome.MATCHED);
    assertThat(
            MatchingStrategies.reference()
                .match(left, differentReference, MatchingContext.strict())
                .outcome())
        .isEqualTo(MatchOutcome.NOT_MATCHED);
  }

  @Test
  void compositeAllOfRequiresEveryStrategyToAgree() {
    MatchCandidate left = candidate("100.00", Reference.payment("PAY-1"));
    MatchCandidate sameAmountDifferentReference = candidate("100.00", Reference.payment("PAY-2"));

    MatchResult result =
        MatchingStrategies.allOf(
                List.of(MatchingStrategies.amount(), MatchingStrategies.reference()))
            .match(left, sameAmountDifferentReference, MatchingContext.strict());

    assertThat(result.outcome()).isEqualTo(MatchOutcome.NOT_MATCHED);
  }

  @Test
  void defaultEngineGreedilyAssignsEachLeftItemToItsBestAvailableRightCandidate() {
    MatchCandidate leftA = candidate("100.00", Reference.payment("PAY-1"));
    MatchCandidate leftB = candidate("50.00", Reference.payment("PAY-2"));
    MatchCandidate rightExactA = candidate("100.00", Reference.payment("PAY-1"));
    MatchCandidate rightUnrelated = candidate("999.00", Reference.payment("PAY-9"));

    List<MatchResult> results =
        MatchingEngines.standard()
            .match(
                List.of(leftA, leftB),
                List.of(rightExactA, rightUnrelated),
                MatchingContext.strict());

    assertThat(results).hasSize(2);
    assertThat(results.get(0).outcome()).isEqualTo(MatchOutcome.MATCHED);
    assertThat(results.get(0).counterpart()).contains(rightExactA.id());
    assertThat(results.get(1).outcome()).isEqualTo(MatchOutcome.NOT_MATCHED);
  }

  private MatchCandidate candidate(String amount, Reference reference) {
    return MatchCandidate.of(
        ReconciliationItem.of(reference, Money.of(new BigDecimal(amount), USD)));
  }

  private MatchCandidate candidate(String amount, Reference reference, Instant occurredAt) {
    return MatchCandidate.of(
        ReconciliationItem.of(reference, Money.of(new BigDecimal(amount), USD)), occurredAt);
  }
}
