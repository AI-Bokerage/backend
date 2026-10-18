package io.genfin.money.internal.allocation;

import io.genfin.money.currency.Currency;
import io.genfin.money.exception.AllocationException;
import io.genfin.money.money.Money;
import io.genfin.money.port.allocation.AllocationStrategy;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The largest-remainder (Hamilton) method: every share gets its floor, then the leftover minor
 * units go one-by-one to the shares with the largest fractional remainder — so the shares always
 * sum to exactly the original total, no rounding loss, no invented cents.
 */
public final class LargestRemainderAllocationStrategy implements AllocationStrategy {

  @Override
  public List<Money> allocate(Money total, List<BigDecimal> weights) {
    if (weights.isEmpty()) {
      throw new AllocationException("Cannot allocate across an empty set of weights.");
    }
    BigDecimal sumWeights = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    if (sumWeights.signum() <= 0 || weights.stream().anyMatch(w -> w.signum() < 0)) {
      throw new AllocationException("Weights must be non-negative and sum to a positive value.");
    }

    Currency currency = total.currency();
    int scale = currency.fractionDigits();
    BigDecimal factor = BigDecimal.TEN.pow(scale);
    int sign = total.amount().signum();

    long totalMinorUnits =
        total.amount().abs().multiply(factor).setScale(0, RoundingMode.HALF_UP).longValueExact();

    long[] baseShares = new long[weights.size()];
    BigDecimal[] remainders = new BigDecimal[weights.size()];
    long distributed = 0;

    for (int i = 0; i < weights.size(); i++) {
      BigDecimal rawShare =
          BigDecimal.valueOf(totalMinorUnits)
              .multiply(weights.get(i))
              .divide(sumWeights, MathContext.DECIMAL64);
      long base = rawShare.setScale(0, RoundingMode.DOWN).longValueExact();
      baseShares[i] = base;
      remainders[i] = rawShare.subtract(BigDecimal.valueOf(base));
      distributed += base;
    }

    long remaining = totalMinorUnits - distributed;
    List<Integer> byRemainderDesc = new ArrayList<>();
    for (int i = 0; i < weights.size(); i++) {
      byRemainderDesc.add(i);
    }
    byRemainderDesc.sort(Comparator.<Integer, BigDecimal>comparing(i -> remainders[i]).reversed());
    for (int i = 0; i < remaining; i++) {
      baseShares[byRemainderDesc.get(i)]++;
    }

    List<Money> shares = new ArrayList<>(weights.size());
    for (long baseShare : baseShares) {
      BigDecimal shareAmount = BigDecimal.valueOf(sign * baseShare).movePointLeft(scale);
      shares.add(Money.of(shareAmount, currency));
    }
    return shares;
  }
}
