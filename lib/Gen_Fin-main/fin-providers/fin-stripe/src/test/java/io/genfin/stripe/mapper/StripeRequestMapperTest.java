package io.genfin.stripe.mapper;

import static io.genfin.stripe.support.TestFixtures.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.metadata.PaymentMetadata;
import io.genfin.payment.method.MethodBuilder;
import io.genfin.payment.method.StandardPaymentMethodType;
import io.genfin.providerapi.capability.CaptureMode;
import io.genfin.stripe.config.ConfirmationMethod;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripeRequestMapperTest {

  @Test
  void createParamsConvertMoneyToMinorUnitsAndLowercaseCurrency() {
    GatewayRequest request =
        new GatewayRequest(
            Money.of("19.99", USD),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("**** 4242")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            new PaymentMetadata(Map.of("orderId", "ord-1")));

    var params =
        StripeRequestMapper.toCreateParams(request, CaptureMode.MANUAL, ConfirmationMethod.MANUAL);

    assertThat(params.getAmount()).isEqualTo(1999L);
    assertThat(params.getCurrency()).isEqualTo("usd");
    assertThat(params.getMetadata()).containsEntry("orderId", "ord-1");
    assertThat(params.getCaptureMethod())
        .isEqualTo(com.stripe.param.PaymentIntentCreateParams.CaptureMethod.MANUAL);
    assertThat(params.getConfirmationMethod())
        .isEqualTo(com.stripe.param.PaymentIntentCreateParams.ConfirmationMethod.MANUAL);
    assertThat(params.getConfirm()).isFalse();
  }

  @Test
  void createParamsUseAutomaticCaptureAndConfirmWhenConfigured() {
    GatewayRequest request =
        new GatewayRequest(
            Money.of("19.99", USD),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("**** 4242")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            null);

    var params =
        StripeRequestMapper.toCreateParams(
            request, CaptureMode.AUTOMATIC, ConfirmationMethod.AUTOMATIC);

    assertThat(params.getCaptureMethod())
        .isEqualTo(com.stripe.param.PaymentIntentCreateParams.CaptureMethod.AUTOMATIC);
    assertThat(params.getConfirmationMethod())
        .isEqualTo(com.stripe.param.PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC);
    assertThat(params.getConfirm()).isTrue();
  }

  @Test
  void createParamsCarryEveryMetadataEntryNotJustTheFirst() {
    GatewayRequest request =
        new GatewayRequest(
            Money.of("19.99", USD),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("**** 4242")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            new PaymentMetadata(Map.of("orderId", "ord-1", "customerId", "cus-1", "note", "n1")));

    var params =
        StripeRequestMapper.toCreateParams(request, CaptureMode.MANUAL, ConfirmationMethod.MANUAL);

    assertThat(params.getMetadata())
        .containsEntry("orderId", "ord-1")
        .containsEntry("customerId", "cus-1")
        .containsEntry("note", "n1")
        .hasSize(3);
  }

  @Test
  void createParamsSetStatementDescriptorWhenProvided() {
    GatewayRequest request =
        new GatewayRequest(
            Money.of("19.99", USD),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("**** 4242")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            null);

    var params =
        StripeRequestMapper.toCreateParams(
            request, CaptureMode.MANUAL, ConfirmationMethod.MANUAL, "ACME SHOP");

    assertThat(params.getStatementDescriptor()).isEqualTo("ACME SHOP");
  }

  @Test
  void createParamsOmitStatementDescriptorWhenNull() {
    GatewayRequest request =
        new GatewayRequest(
            Money.of("19.99", USD),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("**** 4242")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            null);

    var params =
        StripeRequestMapper.toCreateParams(
            request, CaptureMode.MANUAL, ConfirmationMethod.MANUAL, null);

    assertThat(params.getStatementDescriptor()).isNull();
  }

  @Test
  void captureParamsCarryTheRequestedAmount() {
    GatewayRequest request =
        new GatewayRequest(
            Money.of("5.00", USD),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("x")
                .build(),
            null,
            IdempotencyKey.of("k2"),
            null);

    assertThat(StripeRequestMapper.toCaptureParams(request).getAmountToCapture()).isEqualTo(500L);
  }
}
