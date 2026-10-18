package io.genfin.reconciliation.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.comparison.DifferenceSeverity;
import io.genfin.reconciliation.comparison.DifferenceType;
import io.genfin.reconciliation.discrepancy.Discrepancy;
import io.genfin.reconciliation.discrepancy.DiscrepancySeverity;
import io.genfin.reconciliation.discrepancy.StandardDiscrepancyCategory;
import io.genfin.reconciliation.discrepancy.StandardDiscrepancyReason;
import io.genfin.reconciliation.id.ReconciliationBatchId;
import io.genfin.reconciliation.id.ReconciliationId;
import io.genfin.reconciliation.id.ReconciliationItemId;
import io.genfin.reconciliation.matching.MatchResult;
import io.genfin.reconciliation.port.calculation.SummaryCalculator;
import io.genfin.reconciliation.reconciliation.Reconciliation;
import io.genfin.reconciliation.reconciliation.ReconciliationItem;
import io.genfin.reconciliation.summary.ReconciliationSummary;
import io.genfin.refund.reference.Reference;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummaryCalculatorTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency()
          .code("USD")
          .symbol("$")
          .displayName("US Dollar")
          .fractionDigits(2)
          .build();

  private static final SummaryCalculator CALCULATOR = SummaryCalculators.standard();

  @Test
  void tallysMatchOutcomesDifferencesAndDiscrepancySeverities() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());
    reconciliation.addItem(
        ReconciliationItem.of(Reference.payment("PAY-1"), Money.of(new BigDecimal("100.00"), USD)));

    List<MatchResult> matches =
        List.of(
            MatchResult.matched(
                ReconciliationItemId.generate(), ReconciliationItemId.generate(), "exact"),
            MatchResult.partiallyMatched(
                ReconciliationItemId.generate(),
                ReconciliationItemId.generate(),
                0.9,
                Money.of(new BigDecimal("0.50"), USD),
                "tolerance",
                "within tolerance"),
            MatchResult.notMatched(ReconciliationItemId.generate(), "exact"));

    Difference difference =
        Difference.of(
            DifferenceType.AMOUNT,
            DifferenceSeverity.MAJOR,
            "amount",
            "100.00",
            "99.50",
            "amounts differ");
    List<Discrepancy> discrepancies =
        List.of(
            Discrepancy.of(
                StandardDiscrepancyCategory.AMOUNT_MISMATCH,
                DiscrepancySeverity.HIGH,
                StandardDiscrepancyReason.AMOUNT_OUT_OF_TOLERANCE,
                difference,
                "amount mismatch"));

    ReconciliationSummary summary = CALCULATOR.summarize(reconciliation, matches, discrepancies);

    assertThat(summary.matchedCount()).isEqualTo(1);
    assertThat(summary.partiallyMatchedCount()).isEqualTo(1);
    assertThat(summary.unmatchedCount()).isEqualTo(1);
    assertThat(summary.discrepancyCount()).isEqualTo(1);
    assertThat(summary.differenceTotalsByCurrency())
        .containsEntry(USD, Money.of(new BigDecimal("0.50"), USD));
    assertThat(summary.discrepancyCountsBySeverity()).containsEntry(DiscrepancySeverity.HIGH, 1L);
    assertThat(summary.statistics().itemCount()).isEqualTo(1);
    assertThat(summary.statistics().matchAttemptCount()).isEqualTo(3);
    assertThat(summary.statistics().matchRate()).isEqualTo(1.0 / 3.0);
    assertThat(summary.isFullyReconciled()).isFalse();
  }

  @Test
  void emptyInputsProduceAZeroedSummary() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());

    ReconciliationSummary summary = CALCULATOR.summarize(reconciliation, List.of(), List.of());

    assertThat(summary.totalMatchAttempts()).isZero();
    assertThat(summary.differenceTotalsByCurrency()).isEmpty();
    assertThat(summary.statistics().matchRate()).isZero();
    assertThat(summary.isFullyReconciled()).isTrue();
  }
}
