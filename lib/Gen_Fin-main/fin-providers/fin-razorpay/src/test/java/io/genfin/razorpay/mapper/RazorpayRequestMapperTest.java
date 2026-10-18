package io.genfin.razorpay.mapper;

import static io.genfin.razorpay.support.TestFixtures.INR;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.metadata.PaymentMetadata;
import io.genfin.payment.method.MethodBuilder;
import io.genfin.payment.method.StandardPaymentMethodType;
import io.genfin.providerapi.capability.CaptureMode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RazorpayRequestMapperTest {

  @Test
  void orderCreateRequestConvertsMoneyToMinorUnitsAndUppercaseCurrency() {
    GatewayRequest request =
        new GatewayRequest(
            Money.of("199.00", INR),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.UPI)
                .maskedIdentifier("user@bank")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            new PaymentMetadata(Map.of("orderRef", "ord-1")));

    var json = RazorpayRequestMapper.toOrderCreateRequest(request, CaptureMode.MANUAL);

    assertThat(json.getLong("amount")).isEqualTo(19900L);
    assertThat(json.getString("currency")).isEqualTo("INR");
    assertThat(json.getInt("payment_capture")).isEqualTo(0);
    assertThat(json.getJSONObject("notes").getString("orderRef")).isEqualTo("ord-1");
  }

  @Test
  void orderCreateRequestSetsPaymentCaptureFlagWhenAutomatic() {
    GatewayRequest request =
        new GatewayRequest(
            Money.of("199.00", INR),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.UPI)
                .maskedIdentifier("user@bank")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            null);

    var json = RazorpayRequestMapper.toOrderCreateRequest(request, CaptureMode.AUTOMATIC);

    assertThat(json.getInt("payment_capture")).isEqualTo(1);
  }

  @Test
  void orderCreateRequestCarriesEveryNoteEntryNotJustTheFirst() {
    GatewayRequest request =
        new GatewayRequest(
            Money.of("199.00", INR),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.UPI)
                .maskedIdentifier("user@bank")
                .build(),
            null,
            IdempotencyKey.of("k1"),
            new PaymentMetadata(Map.of("orderRef", "ord-1", "customerRef", "cus-1", "note", "n1")));

    var json = RazorpayRequestMapper.toOrderCreateRequest(request, CaptureMode.MANUAL);
    var notes = json.getJSONObject("notes");

    assertThat(notes.getString("orderRef")).isEqualTo("ord-1");
    assertThat(notes.getString("customerRef")).isEqualTo("cus-1");
    assertThat(notes.getString("note")).isEqualTo("n1");
    assertThat(notes.keySet()).hasSize(3);
  }

  @Test
  void captureRequestCarriesAmountAndCurrency() {
    GatewayRequest request =
        new GatewayRequest(
            Money.of("50.00", INR),
            MethodBuilder.newMethod()
                .type(StandardPaymentMethodType.CARD)
                .maskedIdentifier("x")
                .build(),
            null,
            IdempotencyKey.of("k2"),
            null);

    var json = RazorpayRequestMapper.toCaptureRequest(request);

    assertThat(json.getLong("amount")).isEqualTo(5000L);
    assertThat(json.getString("currency")).isEqualTo("INR");
  }
}
