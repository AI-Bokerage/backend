package io.genfin.refund.reason;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.refund.port.reason.RefundReasonRegistry;
import org.junit.jupiter.api.Test;

class RefundReasonTest {

  @Test
  void standardCatalogRegistersEveryStandardReason() {
    RefundReasonRegistry registry =
        RefundReasonRegistries.withProvider(RefundReasonRegistries.standardCatalog());

    assertThat(registry.find(StandardRefundReason.CUSTOMER_REQUEST)).isPresent();
    assertThat(registry.find(StandardRefundReason.CHARGEBACK_RESOLUTION)).isPresent();
    assertThat(registry.findAll()).hasSize(StandardRefundReason.values().length);
  }

  @Test
  void customReasonsCanBeRegistered() {
    RefundReasonRegistry registry = RefundReasonRegistries.empty();
    RefundReason custom = () -> "GOODWILL_CREDIT";

    registry.register(new RefundReasonDescriptor(custom, "Goodwill Credit"));

    assertThat(registry.find(custom)).isPresent();
  }

  @Test
  void requiringAnUnregisteredReasonThrows() {
    RefundReasonRegistry registry = RefundReasonRegistries.empty();

    assertThat(
            org.assertj.core.api.Assertions.catchThrowable(
                () -> registry.require(StandardRefundReason.FRAUD)))
        .isInstanceOf(io.genfin.api.exception.GenFinException.class);
  }
}
