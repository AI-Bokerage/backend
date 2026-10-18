package io.genfin.money.rounding;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RoundingStrategiesTest {

  @Test
  void halfUpRoundsAwayFromZeroAtBoundary() {
    assertThat(RoundingStrategies.HALF_UP.round(new BigDecimal("0.125"), 2))
        .isEqualByComparingTo("0.13");
  }

  @Test
  void halfEvenRoundsToEvenNeighbourAtBoundary() {
    assertThat(RoundingStrategies.HALF_EVEN.round(new BigDecimal("0.125"), 2))
        .isEqualByComparingTo("0.12");
    assertThat(RoundingStrategies.HALF_EVEN.round(new BigDecimal("0.135"), 2))
        .isEqualByComparingTo("0.14");
  }

  @Test
  void downTruncatesTowardZero() {
    assertThat(RoundingStrategies.DOWN.round(new BigDecimal("1.999"), 2))
        .isEqualByComparingTo("1.99");
    assertThat(RoundingStrategies.DOWN.round(new BigDecimal("-1.999"), 2))
        .isEqualByComparingTo("-1.99");
  }

  @Test
  void ceilingAndFloorRespectSign() {
    assertThat(RoundingStrategies.CEILING.round(new BigDecimal("1.001"), 2))
        .isEqualByComparingTo("1.01");
    assertThat(RoundingStrategies.FLOOR.round(new BigDecimal("-1.001"), 2))
        .isEqualByComparingTo("-1.01");
  }

  @Test
  void customStrategyIsHonoured() {
    var alwaysZero = RoundingStrategies.custom((value, scale) -> BigDecimal.ZERO.setScale(scale));

    assertThat(alwaysZero.round(new BigDecimal("999.99"), 2)).isEqualByComparingTo("0.00");
  }
}
