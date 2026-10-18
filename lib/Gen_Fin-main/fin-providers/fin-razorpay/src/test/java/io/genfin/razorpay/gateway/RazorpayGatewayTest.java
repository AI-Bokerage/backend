package io.genfin.razorpay.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.payment.failure.FailureCategory;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.method.MethodBuilder;
import io.genfin.payment.method.StandardPaymentMethodType;
import io.genfin.razorpay.support.TestFixtures;
import org.junit.jupiter.api.Test;

class RazorpayGatewayTest {

  private static final Currency UNSUPPORTED_CURRENCY =
      CurrencyFactory.newCurrency()
          .code("USD")
          .symbol("$")
          .displayName("US Dollar")
          .fractionDigits(2)
          .build();

  @Test
  void authorizeRejectsUnsupportedCurrencyWithoutCallingRazorpay() {
    RazorpayGateway gateway = new RazorpayGateway(TestFixtures.razorpayConfiguration());
    GatewayRequest request =
        new GatewayRequest(
            Money.of("10.00", UNSUPPORTED_CURRENCY),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("x")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            null);

    var response = gateway.authorize(request);

    assertThat(response.success()).isFalse();
    assertThat(response.failure()).isPresent();
    assertThat(response.failure().get().message()).contains("USD");
    assertThat(response.failure().get().category()).isEqualTo(FailureCategory.PROVIDER_ERROR);
  }

  @Test
  void authorizeRejectsCaptureModeMethodCombinationRazorpayCannotHonorWithoutCallingRazorpay() {
    RazorpayGateway gateway = new RazorpayGateway(TestFixtures.razorpayConfiguration());
    GatewayRequest request =
        new GatewayRequest(
            Money.of("10.00", TestFixtures.INR),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.BANK_TRANSFER)
                .maskedIdentifier("acct-1")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            null);

    var response = gateway.authorize(request);

    assertThat(response.success()).isFalse();
    assertThat(response.failure()).isPresent();
    assertThat(response.failure().get().message()).contains("BANK_TRANSFER");
  }
}
