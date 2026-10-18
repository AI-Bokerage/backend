package io.genfin.payment.payment;

import static io.genfin.payment.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.time.ClockProviders;
import io.genfin.money.money.Money;
import io.genfin.payment.method.MethodBuilder;
import io.genfin.payment.method.StandardPaymentMethodType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LargePaymentBatchTest {

  @Test
  void thousandsOfPaymentsEachCaptureIndependently() {
    int count = 3000;
    List<Payment> payments = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      Payment payment =
          PaymentBuilder.newPayment()
              .amount(Money.of("10.00", USD))
              .method(
                  MethodBuilder.newMethod()
                      .type(StandardPaymentMethodType.CARD)
                      .maskedIdentifier("**** 0000")
                      .build())
              .clockProvider(ClockProviders.system())
              .actor("batch")
              .build();
      payment.submit(ClockProviders.system(), "batch");
      payment.authorize(Money.of("10.00", USD), "auth-" + i, ClockProviders.system(), "batch");
      payment.capture(Money.of("10.00", USD), "cap-" + i, ClockProviders.system(), "batch");
      payments.add(payment);
    }

    assertThat(payments).hasSize(count);
    assertThat(payments.stream().map(Payment::id).distinct()).hasSize(count);
    assertThat(payments)
        .allSatisfy(p -> assertThat(p.totalCaptured()).isEqualTo(Money.of("10.00", USD)));
  }
}
