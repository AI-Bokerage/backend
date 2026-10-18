package io.genfin.stripe.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.stripe.net.RequestOptions;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.payment.failure.FailureCategory;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.method.MethodBuilder;
import io.genfin.payment.method.StandardPaymentMethodType;
import io.genfin.providerapi.capability.CaptureMode;
import io.genfin.stripe.config.ConfirmationMethod;
import io.genfin.stripe.support.TestFixtures;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StripeGatewayTest {

  private static final Currency UNSUPPORTED_CURRENCY =
      CurrencyFactory.newCurrency()
          .code("XYZ")
          .symbol("X")
          .displayName("Not a real currency")
          .fractionDigits(2)
          .build();

  @Test
  void authorizeRejectsUnsupportedCurrencyWithoutCallingStripe() {
    StripeGateway gateway = new StripeGateway(TestFixtures.stripeConfiguration());
    GatewayRequest request =
        new GatewayRequest(
            Money.of("10.00", UNSUPPORTED_CURRENCY),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("**** 4242")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            null);

    var response = gateway.authorize(request);

    assertThat(response.success()).isFalse();
    assertThat(response.failure()).isPresent();
    assertThat(response.failure().get().message()).contains("XYZ");
    assertThat(response.failure().get().category()).isEqualTo(FailureCategory.PROVIDER_ERROR);
  }

  @Test
  void authorizeRejectsCaptureModeMethodCombinationStripeCannotHonorWithoutCallingStripe() {
    StripeGateway gateway = new StripeGateway(TestFixtures.stripeConfiguration());
    GatewayRequest request =
        new GatewayRequest(
            Money.of("10.00", TestFixtures.USD),
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

  @Test
  void requestOptionsCarryIdempotencyKeyApiVersionAndTimeoutFromConfiguration() {
    StripeGateway gateway =
        new StripeGateway(
            TestFixtures.stripeConfiguration(
                CaptureMode.AUTOMATIC,
                ConfirmationMethod.AUTOMATIC,
                "2024-06-20",
                Duration.ofSeconds(5)));
    GatewayRequest request =
        new GatewayRequest(
            Money.of("10.00", TestFixtures.USD),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("**** 4242")
                .build(),
            null,
            IdempotencyKey.of("idem-key-1"),
            null);

    RequestOptions options = gateway.requestOptions(request);

    assertThat(options.getIdempotencyKey()).isEqualTo("idem-key-1");
    assertThat(RequestOptions.unsafeGetStripeVersionOverride(options)).isEqualTo("2024-06-20");
    assertThat(options.getConnectTimeout()).isEqualTo(5000);
    assertThat(options.getReadTimeout()).isEqualTo(5000);
  }

  @Test
  void requestOptionsOmitStripeVersionOverrideWhenNotConfigured() {
    StripeGateway gateway = new StripeGateway(TestFixtures.stripeConfiguration());
    GatewayRequest request =
        new GatewayRequest(
            Money.of("10.00", TestFixtures.USD),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("**** 4242")
                .build(),
            null,
            IdempotencyKey.of("idem-key-2"),
            null);

    RequestOptions options = gateway.requestOptions(request);

    assertThat(options.getIdempotencyKey()).isEqualTo("idem-key-2");
    assertThat(RequestOptions.unsafeGetStripeVersionOverride(options)).isNull();
  }
}
