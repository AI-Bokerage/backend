package io.genfin.refund.refund;

import static io.genfin.refund.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.money.money.Money;
import io.genfin.refund.lifecycle.StandardRefundStatus;
import io.genfin.refund.reference.Reference;
import org.junit.jupiter.api.Test;

class RefundBuilderAndFactoryTest {

  @Test
  void builderDefaultsTypeDirectionAndLifecycleWhenUnset() {
    Refund refund =
        RefundBuilder.newRefund()
            .refundNumber(RefundNumber.of("RFD-1"))
            .amount(Money.of("10.00", USD))
            .paymentReference(Reference.payment("payment-1"))
            .build();

    assertThat(refund.type()).isEqualTo(StandardRefundType.FULL);
    assertThat(refund.direction()).isEqualTo(RefundDirection.OUTBOUND);
    assertThat(refund.status()).isEqualTo(StandardRefundStatus.REQUESTED);
  }

  @Test
  void factoryFallsBackToStandardLifecycleWhenNoneRegistered() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    Refund refund =
        RefundFactory.create(
            registry,
            RefundNumber.of("RFD-2"),
            Money.of("25.00", USD),
            StandardRefundType.PARTIAL,
            RefundDirection.OUTBOUND,
            Reference.payment("payment-2"));

    assertThat(refund.type()).isEqualTo(StandardRefundType.PARTIAL);
    assertThat(refund.status()).isEqualTo(StandardRefundStatus.REQUESTED);
  }
}
