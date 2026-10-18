package io.genfin.money.allocation;

import io.genfin.money.internal.allocation.LargestRemainderAllocationStrategy;
import io.genfin.money.money.Money;
import io.genfin.money.port.allocation.AllocationStrategy;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Factory for {@link AllocationStrategy}. Equal/ratio/percentage/weighted are all the same lossless
 * largest-remainder engine underneath — they only differ in how the caller expresses the weights.
 */
public final class AllocationStrategies {

  private static final AllocationStrategy LARGEST_REMAINDER =
      new LargestRemainderAllocationStrategy();

  private AllocationStrategies() {}

  public static AllocationStrategy largestRemainder() {
    return LARGEST_REMAINDER;
  }

  public static List<Money> equal(Money total, int parts) {
    List<BigDecimal> weights = Collections.nCopies(parts, BigDecimal.ONE);
    return LARGEST_REMAINDER.allocate(total, weights);
  }

  public static List<Money> byRatio(Money total, List<Long> ratios) {
    return LARGEST_REMAINDER.allocate(total, ratios.stream().map(BigDecimal::valueOf).toList());
  }

  public static List<Money> byPercentage(Money total, List<BigDecimal> percentages) {
    return LARGEST_REMAINDER.allocate(total, percentages);
  }

  public static List<Money> byWeight(Money total, List<BigDecimal> weights) {
    return LARGEST_REMAINDER.allocate(total, weights);
  }
}
