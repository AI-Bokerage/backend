package io.genfin.payment.method;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.payment.port.method.PaymentMethodRegistry;
import org.junit.jupiter.api.Test;

class PaymentMethodTest {

  @Test
  void methodBuilderProducesAMaskedInstance() {
    PaymentMethod method =
        MethodBuilder.newMethod()
            .type(StandardPaymentMethodType.CARD)
            .maskedIdentifier("**** 4242")
            .build();

    assertThat(method.type()).isEqualTo(StandardPaymentMethodType.CARD);
    assertThat(method.maskedIdentifier()).isEqualTo("**** 4242");
  }

  @Test
  void standardCatalogRegistersEveryStandardType() {
    PaymentMethodRegistry registry =
        PaymentMethodRegistries.withProvider(PaymentMethodRegistries.standardCatalog());

    assertThat(registry.find(StandardPaymentMethodType.CARD)).isPresent();
    assertThat(registry.find(StandardPaymentMethodType.UPI)).isPresent();
    assertThat(registry.findAll()).hasSize(StandardPaymentMethodType.values().length);
  }

  @Test
  void customMethodsCanBeRegistered() {
    PaymentMethodRegistry registry = PaymentMethodRegistries.empty();
    PaymentMethodType custom = () -> "BUY_NOW_PAY_LATER";

    registry.register(
        new PaymentMethodDescriptor(
            custom, "Buy Now Pay Later", PaymentMethodCapabilities.fullyCapable()));

    assertThat(registry.find(custom)).isPresent();
  }
}
