package io.genfin.reconciliation.internal.discrepancy;

import io.genfin.reconciliation.comparison.Difference;
import io.genfin.reconciliation.comparison.DifferenceSeverity;
import io.genfin.reconciliation.discrepancy.Discrepancy;
import io.genfin.reconciliation.discrepancy.DiscrepancyCategory;
import io.genfin.reconciliation.discrepancy.DiscrepancyReason;
import io.genfin.reconciliation.discrepancy.DiscrepancySeverity;
import io.genfin.reconciliation.discrepancy.StandardDiscrepancyCategory;
import io.genfin.reconciliation.discrepancy.StandardDiscrepancyReason;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyPolicy;
import java.util.Optional;

/**
 * The standard classification: a {@link DifferenceSeverity#MINOR} difference is explainable (e.g.
 * still inside tolerance) and never becomes a discrepancy; {@code MAJOR} maps to {@link
 * DiscrepancySeverity#MEDIUM} and {@code CRITICAL} to {@link DiscrepancySeverity#CRITICAL}.
 * Category and reason are derived from the difference's {@code DifferenceType} one-for-one.
 */
public final class DefaultDiscrepancyPolicy implements DiscrepancyPolicy {

  @Override
  public Optional<Discrepancy> classify(Difference difference) {
    if (difference.severity() == DifferenceSeverity.MINOR) {
      return Optional.empty();
    }
    DiscrepancySeverity severity =
        difference.severity() == DifferenceSeverity.CRITICAL
            ? DiscrepancySeverity.CRITICAL
            : DiscrepancySeverity.MEDIUM;
    DiscrepancyCategory category = categoryFor(difference);
    DiscrepancyReason reason = reasonFor(difference);
    return Optional.of(
        Discrepancy.of(category, severity, reason, difference, difference.description()));
  }

  private static DiscrepancyCategory categoryFor(Difference difference) {
    return switch (difference.type()) {
      case AMOUNT -> StandardDiscrepancyCategory.AMOUNT_MISMATCH;
      case CURRENCY -> StandardDiscrepancyCategory.CURRENCY_MISMATCH;
      case REFERENCE -> StandardDiscrepancyCategory.REFERENCE_MISMATCH;
      case STATUS -> StandardDiscrepancyCategory.STATUS_MISMATCH;
      case TIMESTAMP -> StandardDiscrepancyCategory.TIMING_MISMATCH;
      case METADATA -> StandardDiscrepancyCategory.METADATA_MISMATCH;
      case ATTRIBUTE -> StandardDiscrepancyCategory.ATTRIBUTE_MISMATCH;
    };
  }

  private static DiscrepancyReason reasonFor(Difference difference) {
    return switch (difference.type()) {
      case AMOUNT -> StandardDiscrepancyReason.AMOUNT_OUT_OF_TOLERANCE;
      case CURRENCY -> StandardDiscrepancyReason.CURRENCY_MISMATCH;
      case REFERENCE -> StandardDiscrepancyReason.REFERENCE_MISMATCH;
      case STATUS -> StandardDiscrepancyReason.STATUS_MISMATCH;
      case TIMESTAMP -> StandardDiscrepancyReason.TIMESTAMP_DRIFT;
      case METADATA -> StandardDiscrepancyReason.METADATA_MISMATCH;
      case ATTRIBUTE -> StandardDiscrepancyReason.ATTRIBUTE_MISMATCH;
    };
  }
}
