package io.genfin.reconciliation.discrepancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.reconciliation.comparison.ComparisonResult;
import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.comparison.DifferenceSeverity;
import io.genfin.reconciliation.comparison.DifferenceType;
import io.genfin.reconciliation.id.ComparisonId;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiscrepancyDetectorTest {

  @Test
  void minorDifferencesAreNotDiscrepancies() {
    Difference minor =
        Difference.of(DifferenceType.AMOUNT, DifferenceSeverity.MINOR, "amount", "1", "2", "d");

    assertThat(DiscrepancyPolicies.standard().classify(minor)).isEmpty();
  }

  @Test
  void majorDifferenceBecomesMediumSeverityDiscrepancy() {
    Difference major =
        Difference.of(DifferenceType.AMOUNT, DifferenceSeverity.MAJOR, "amount", "1", "10", "d");

    Discrepancy discrepancy = DiscrepancyPolicies.standard().classify(major).orElseThrow();

    assertThat(discrepancy.category()).isEqualTo(StandardDiscrepancyCategory.AMOUNT_MISMATCH);
    assertThat(discrepancy.severity()).isEqualTo(DiscrepancySeverity.MEDIUM);
    assertThat(discrepancy.reason()).isEqualTo(StandardDiscrepancyReason.AMOUNT_OUT_OF_TOLERANCE);
    assertThat(discrepancy.resolution()).isEqualTo(DiscrepancyResolution.UNRESOLVED);
    assertThat(discrepancy.isResolved()).isFalse();
  }

  @Test
  void criticalDifferenceBecomesCriticalSeverityDiscrepancy() {
    Difference critical =
        Difference.of(
            DifferenceType.CURRENCY, DifferenceSeverity.CRITICAL, "currency", "USD", "EUR", "d");

    Discrepancy discrepancy = DiscrepancyPolicies.standard().classify(critical).orElseThrow();

    assertThat(discrepancy.category()).isEqualTo(StandardDiscrepancyCategory.CURRENCY_MISMATCH);
    assertThat(discrepancy.severity()).isEqualTo(DiscrepancySeverity.CRITICAL);
    assertThat(discrepancy.reason()).isEqualTo(StandardDiscrepancyReason.CURRENCY_MISMATCH);
  }

  @Test
  void resolveReturnsNewInstanceWithUpdatedResolution() {
    Difference major =
        Difference.of(DifferenceType.STATUS, DifferenceSeverity.MAJOR, "status", "A", "B", "d");
    Discrepancy discrepancy = DiscrepancyPolicies.standard().classify(major).orElseThrow();

    Discrepancy resolved = discrepancy.resolve(DiscrepancyResolution.ACCEPTED);

    assertThat(resolved.isResolved()).isTrue();
    assertThat(resolved.resolution()).isEqualTo(DiscrepancyResolution.ACCEPTED);
    assertThat(discrepancy.resolution()).isEqualTo(DiscrepancyResolution.UNRESOLVED);
  }

  @Test
  void detectorCollectsOnlyReportableDiscrepanciesFromAComparisonResult() {
    Difference minor =
        Difference.of(DifferenceType.AMOUNT, DifferenceSeverity.MINOR, "amount", "1", "1.1", "d");
    Difference major =
        Difference.of(
            DifferenceType.REFERENCE, DifferenceSeverity.MAJOR, "reference", "A", "B", "d");
    ComparisonResult result = new ComparisonResult(ComparisonId.generate(), List.of(minor, major));

    List<Discrepancy> discrepancies = DiscrepancyDetectors.standard().detect(result);

    assertThat(discrepancies).hasSize(1);
    assertThat(discrepancies.get(0).difference()).isEqualTo(major);
  }

  @Test
  void standardCatalogHasADescriptorForEveryStandardReason() {
    var registry =
        DiscrepancyReasonRegistries.withProvider(DiscrepancyReasonRegistries.standardCatalog());

    for (StandardDiscrepancyReason reason : StandardDiscrepancyReason.values()) {
      assertThat(registry.find(reason)).isPresent();
    }
  }
}
