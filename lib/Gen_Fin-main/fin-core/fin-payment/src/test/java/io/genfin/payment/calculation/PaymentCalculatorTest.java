package io.genfin.payment.calculation;

import static io.genfin.payment.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.payment.id.PaymentId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PaymentCalculatorTest {

  @Test
  void underpaymentLeavesPositiveRemaining() {
    PaymentBalance balance =
        PaymentCalculators.standard()
            .calculateBalance(Money.of("100.00", USD), List.of(Money.of("60.00", USD)));

    assertThat(balance.isUnderpaid()).isTrue();
    assertThat(balance.remaining()).isEqualTo(Money.of("40.00", USD));
    assertThat(OutstandingAmount.from(balance).amount()).isEqualTo(Money.of("40.00", USD));
  }

  @Test
  void overpaymentLeavesNegativeRemainingAndZeroOutstanding() {
    PaymentBalance balance =
        PaymentCalculators.standard()
            .calculateBalance(Money.of("100.00", USD), List.of(Money.of("120.00", USD)));

    assertThat(balance.isOverpaid()).isTrue();
    assertThat(OutstandingAmount.from(balance).amount()).isEqualTo(Money.zero(USD));
  }

  @Test
  void exactPaymentIsSettled() {
    PaymentBalance balance =
        PaymentCalculators.standard()
            .calculateBalance(Money.of("100.00", USD), List.of(Money.of("100.00", USD)));

    assertThat(balance.isSettled()).isTrue();
  }

  @Test
  void multiplePaymentsSumTowardBalance() {
    PaymentBalance balance =
        PaymentCalculators.standard()
            .calculateBalance(
                Money.of("100.00", USD),
                List.of(Money.of("30.00", USD), Money.of("30.00", USD), Money.of("40.00", USD)));

    assertThat(balance.isSettled()).isTrue();
  }

  @Test
  void settlementSubtractsFeesFromGross() {
    SettlementResult result =
        PaymentCalculators.standard().settle(Money.of("100.00", USD), Money.of("3.00", USD));

    assertThat(result.netAmount()).isEqualTo(Money.of("97.00", USD));
  }

  @Test
  void allocationRespectsInsertionOrderAndNeverExceedsTotalDue() {
    PaymentId first = PaymentId.generate();
    PaymentId second = PaymentId.generate();
    Map<PaymentId, Money> captured = new LinkedHashMap<>();
    captured.put(first, Money.of("70.00", USD));
    captured.put(second, Money.of("70.00", USD));

    List<PaymentAllocation> allocations =
        PaymentCalculators.standard().allocate(Money.of("100.00", USD), captured);

    assertThat(allocations.get(0).allocatedAmount()).isEqualTo(Money.of("70.00", USD));
    assertThat(allocations.get(1).allocatedAmount()).isEqualTo(Money.of("30.00", USD));
  }
}
