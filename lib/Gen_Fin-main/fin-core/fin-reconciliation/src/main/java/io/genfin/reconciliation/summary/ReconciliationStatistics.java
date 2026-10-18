package io.genfin.reconciliation.summary;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * General point-in-time statistics about a reconciliation run, derived alongside a {@link
 * ReconciliationSummary}.
 */
public record ReconciliationStatistics(
    int itemCount, int matchAttemptCount, int discrepancyCount, double matchRate)
    implements ValueObject {

  public ReconciliationStatistics {
    Validate.nonNegative(itemCount, "itemCount must not be negative.");
    Validate.nonNegative(matchAttemptCount, "matchAttemptCount must not be negative.");
    Validate.nonNegative(discrepancyCount, "discrepancyCount must not be negative.");
    Validate.required(
        matchRate >= 0.0 && matchRate <= 1.0, "matchRate must be between 0.0 and 1.0.");
  }
}
