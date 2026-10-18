package io.genfin.reconciliation.comparison;

import io.genfin.reconciliation.internal.comparison.AmountComparison;
import io.genfin.reconciliation.internal.comparison.AttributeComparison;
import io.genfin.reconciliation.internal.comparison.CurrencyComparison;
import io.genfin.reconciliation.internal.comparison.MetadataComparison;
import io.genfin.reconciliation.internal.comparison.ReferenceComparison;
import io.genfin.reconciliation.internal.comparison.StatusComparison;
import io.genfin.reconciliation.internal.comparison.TimestampComparison;
import io.genfin.reconciliation.port.comparison.ComparisonStrategy;
import java.util.List;

/** Factory for the default {@link ComparisonStrategy} implementations. */
public final class ComparisonStrategies {

  private ComparisonStrategies() {}

  public static ComparisonStrategy amount() {
    return new AmountComparison();
  }

  public static ComparisonStrategy currency() {
    return new CurrencyComparison();
  }

  public static ComparisonStrategy reference() {
    return new ReferenceComparison();
  }

  public static ComparisonStrategy status() {
    return new StatusComparison();
  }

  public static ComparisonStrategy timestamp() {
    return new TimestampComparison();
  }

  public static ComparisonStrategy metadata() {
    return new MetadataComparison();
  }

  public static ComparisonStrategy attributes() {
    return new AttributeComparison();
  }

  /** Every default strategy — currency and amount first so a currency mismatch reads first. */
  public static List<ComparisonStrategy> standardChain() {
    return List.of(
        currency(), amount(), reference(), status(), timestamp(), metadata(), attributes());
  }
}
