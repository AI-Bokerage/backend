package io.genfin.refund.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.payment.method.PaymentMethod;
import io.genfin.payment.method.StandardPaymentMethodType;
import io.genfin.refund.id.RefundId;
import io.genfin.refund.support.FakePaymentGateway;
import io.genfin.refund.support.TestCurrencies;
import org.junit.jupiter.api.Test;

class RefundGatewayOperationTest {

  private final RefundGatewayRequest request =
      new RefundGatewayRequest(
          RefundId.generate(),
          Money.of(10, TestCurrencies.USD),
          PaymentMethod.of(StandardPaymentMethodType.CARD, "****4242"),
          null,
          null);

  @Test
  void mapsASuccessfulGatewayResponseToARefundGatewayResponse() {
    RefundGatewayResponse response =
        RefundGatewayOperation.execute(new FakePaymentGateway(true), request);

    assertThat(response.success()).isTrue();
    assertThat(response.reference()).contains("gw-refund-ref");
    assertThat(response.failure()).isEmpty();
  }

  @Test
  void mapsAFailedGatewayResponseToARefundGatewayResponse() {
    RefundGatewayResponse response =
        RefundGatewayOperation.execute(new FakePaymentGateway(false), request);

    assertThat(response.success()).isFalse();
    assertThat(response.reference()).isEmpty();
    assertThat(response.failure()).isPresent();
  }
}
