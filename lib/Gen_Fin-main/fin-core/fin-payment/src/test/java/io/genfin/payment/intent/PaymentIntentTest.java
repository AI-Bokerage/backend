package io.genfin.payment.intent;

import static io.genfin.payment.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.money.money.Money;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.payment.StandardPaymentPurpose;
import io.genfin.payment.reference.Reference;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentIntentTest {

  @Test
  void builderProducesAValidIntent() {
    PaymentIntent intent =
        IntentBuilder.newIntent()
            .amount(Money.of("50.00", USD))
            .invoiceReference(Reference.invoice("INV-1"))
            .purpose(StandardPaymentPurpose.INVOICE_PAYMENT)
            .idempotencyKey(IdempotencyKey.of("key-1"))
            .expiresAt(Instant.parse("2026-02-01T00:00:00Z"))
            .build();

    assertThat(intent.amount()).isEqualTo(Money.of("50.00", USD));
    assertThat(intent.isExpired(Instant.parse("2026-03-01T00:00:00Z"))).isTrue();
    assertThat(intent.isExpired(Instant.parse("2026-01-01T00:00:00Z"))).isFalse();
  }

  @Test
  void zeroOrNegativeAmountIsRejected() {
    assertThatThrownBy(
            () ->
                IntentBuilder.newIntent()
                    .amount(Money.zero(USD))
                    .purpose(StandardPaymentPurpose.INVOICE_PAYMENT)
                    .idempotencyKey(IdempotencyKey.of("key-1"))
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
