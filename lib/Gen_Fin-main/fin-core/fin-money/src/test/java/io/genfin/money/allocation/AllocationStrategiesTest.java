package io.genfin.money.allocation;

import static io.genfin.money.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.money.exception.AllocationException;
import io.genfin.money.money.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AllocationStrategiesTest {

  @Test
  void equalSplitOfNonDivisibleAmountLosesNoCents() {
    List<Money> shares = AllocationStrategies.equal(Money.of("10.00", USD), 3);

    assertThat(shares)
        .containsExactly(Money.of("3.34", USD), Money.of("3.33", USD), Money.of("3.33", USD));
    assertThat(sum(shares)).isEqualTo(Money.of("10.00", USD));
  }

  @Test
  void ratioAllocationSumsExactlyToTotal() {
    List<Money> shares = AllocationStrategies.byRatio(Money.of("100.00", USD), List.of(1L, 2L, 3L));

    assertThat(sum(shares)).isEqualTo(Money.of("100.00", USD));
    assertThat(shares.get(2).compareTo(shares.get(0))).isPositive();
  }

  @Test
  void percentageAllocationSumsExactlyToTotal() {
    List<Money> shares =
        AllocationStrategies.byPercentage(
            Money.of("99.99", USD),
            List.of(new BigDecimal("33.33"), new BigDecimal("33.33"), new BigDecimal("33.34")));

    assertThat(sum(shares)).isEqualTo(Money.of("99.99", USD));
  }

  @Test
  void negativeTotalAllocatesWithConsistentSign() {
    List<Money> shares = AllocationStrategies.equal(Money.of("-10.00", USD), 3);

    assertThat(sum(shares)).isEqualTo(Money.of("-10.00", USD));
    assertThat(shares).allSatisfy(share -> assertThat(share.isPositive()).isFalse());
  }

  @Test
  void zeroWeightsAreRejected() {
    assertThatThrownBy(
            () ->
                AllocationStrategies.byWeight(
                    Money.of("10.00", USD), List.of(BigDecimal.ZERO, BigDecimal.ZERO)))
        .isInstanceOf(AllocationException.class);
  }

  @Test
  void emptyWeightsAreRejected() {
    assertThatThrownBy(() -> AllocationStrategies.byWeight(Money.of("10.00", USD), List.of()))
        .isInstanceOf(AllocationException.class);
  }

  @Test
  void propertyCheckManyRandomSplitsNeverLoseOrInventValue() {
    Random random = new Random(42);
    for (int i = 0; i < 200; i++) {
      BigDecimal amount = BigDecimal.valueOf(random.nextInt(1_000_000), 2);
      Money total = Money.of(amount, USD);
      int parts = 1 + random.nextInt(11);
      List<BigDecimal> weights =
          random.doubles(parts, 0.01, 100).mapToObj(BigDecimal::valueOf).toList();

      List<Money> shares = AllocationStrategies.byWeight(total, weights);

      assertThat(sum(shares)).isEqualTo(total);
      assertThat(shares).hasSize(parts);
    }
  }

  private static Money sum(List<Money> shares) {
    return shares.stream().reduce(Money.zero(USD), Money::add);
  }
}
