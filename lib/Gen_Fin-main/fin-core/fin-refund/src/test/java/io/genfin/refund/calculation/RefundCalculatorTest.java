package io.genfin.refund.calculation;

import static io.genfin.refund.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.refund.id.RefundId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RefundCalculatorTest {

  @Test
  void fullRefundOfUntouchedPaymentConsumesWholeBalance() {
    RefundCalculationResult result =
        RefundCalculators.standard()
            .calculate(
                new RefundCalculationContext(
                    Money.of("100.00", USD), List.of(), Money.of("100.00", USD)));

    assertThat(result.approvedAmount()).isEqualTo(Money.of("100.00", USD));
    assertThat(result.wasCapped()).isFalse();
    assertThat(result.isFullRefund()).isTrue();
    assertThat(result.balanceBeforeRequest().refundableRemaining())
        .isEqualTo(Money.of("100.00", USD));
  }

  @Test
  void partialRefundLeavesRemainingRefundableBalance() {
    RefundCalculationResult result =
        RefundCalculators.standard()
            .calculate(
                new RefundCalculationContext(
                    Money.of("100.00", USD), List.of(), Money.of("40.00", USD)));

    assertThat(result.approvedAmount()).isEqualTo(Money.of("40.00", USD));
    assertThat(result.isFullRefund()).isFalse();
    RefundBalance after =
        RefundCalculators.standard()
            .calculateBalance(Money.of("100.00", USD), List.of(Money.of("40.00", USD)));
    assertThat(after.refundableRemaining()).isEqualTo(Money.of("60.00", USD));
  }

  @Test
  void multipleRefundsAgainstOnePaymentSumTowardBalance() {
    RefundBalance balance =
        RefundCalculators.standard()
            .calculateBalance(
                Money.of("100.00", USD),
                List.of(Money.of("30.00", USD), Money.of("20.00", USD), Money.of("10.00", USD)));

    assertThat(balance.totalRefunded()).isEqualTo(Money.of("60.00", USD));
    assertThat(balance.isPartiallyRefunded()).isTrue();
    assertThat(balance.refundableRemaining()).isEqualTo(Money.of("40.00", USD));
  }

  @Test
  void requestExceedingRemainingBalanceIsCappedNotOverRefunded() {
    RefundCalculationResult result =
        RefundCalculators.standard()
            .calculate(
                new RefundCalculationContext(
                    Money.of("100.00", USD),
                    List.of(Money.of("80.00", USD)),
                    Money.of("50.00", USD)));

    assertThat(result.wasCapped()).isTrue();
    assertThat(result.approvedAmount()).isEqualTo(Money.of("20.00", USD));
  }

  @Test
  void fullyRefundedPaymentApprovesNothingFurther() {
    RefundCalculationResult result =
        RefundCalculators.standard()
            .calculate(
                new RefundCalculationContext(
                    Money.of("100.00", USD),
                    List.of(Money.of("100.00", USD)),
                    Money.of("10.00", USD)));

    assertThat(result.approvedAmount()).isEqualTo(Money.zero(USD));
    assertThat(result.balanceBeforeRequest().isFullyRefunded()).isTrue();
  }

  @Test
  void allocationRespectsInsertionOrderAndNeverExceedsRefundableTotal() {
    RefundId first = RefundId.generate();
    RefundId second = RefundId.generate();
    Map<RefundId, Money> requested = new LinkedHashMap<>();
    requested.put(first, Money.of("70.00", USD));
    requested.put(second, Money.of("70.00", USD));

    List<RefundAllocation> allocations =
        RefundCalculators.standard().allocate(Money.of("100.00", USD), requested);

    assertThat(allocations.get(0).allocatedAmount()).isEqualTo(Money.of("70.00", USD));
    assertThat(allocations.get(1).allocatedAmount()).isEqualTo(Money.of("30.00", USD));
  }
}
