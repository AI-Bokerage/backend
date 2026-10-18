package io.genfin.invoice.adjustment;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdjustmentEngineTest {

  @Test
  void applyAllFoldsSignedAmountsOntoBase() {
    List<Adjustment> adjustments =
        List.of(
            Adjustment.of(StandardAdjustmentType.FEE, Money.of("5.00", USD), "processing fee"),
            Adjustment.of(
                StandardAdjustmentType.CREDIT, Money.of("-2.00", USD), "goodwill credit"));

    Money result = AdjustmentEngines.standard().applyAll(adjustments, Money.of("100.00", USD));

    assertThat(result).isEqualTo(Money.of("103.00", USD));
  }

  @Test
  void emptyAdjustmentsLeaveBaseUnchanged() {
    Money result = AdjustmentEngines.standard().applyAll(List.of(), Money.of("50.00", USD));

    assertThat(result).isEqualTo(Money.of("50.00", USD));
  }
}
