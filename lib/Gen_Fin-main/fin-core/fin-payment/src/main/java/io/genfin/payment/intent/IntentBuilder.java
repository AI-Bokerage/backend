package io.genfin.payment.intent;

import io.genfin.money.money.Money;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.metadata.PaymentMetadata;
import io.genfin.payment.payment.PaymentPurpose;
import io.genfin.payment.payment.StandardPaymentPurpose;
import io.genfin.payment.reference.Reference;
import java.time.Instant;

/**
 * Builds {@link PaymentIntent}s. Preferred over the canonical constructor for readability at call
 * sites.
 */
public final class IntentBuilder {

  private Money amount;
  private Reference invoiceReference;
  private Reference customerReference;
  private Instant expiresAt;
  private PaymentMetadata metadata = PaymentMetadata.empty();
  private PaymentPurpose purpose = StandardPaymentPurpose.INVOICE_PAYMENT;
  private IdempotencyKey idempotencyKey;

  private IntentBuilder() {}

  public static IntentBuilder newIntent() {
    return new IntentBuilder();
  }

  public IntentBuilder amount(Money amount) {
    this.amount = amount;
    return this;
  }

  public IntentBuilder invoiceReference(Reference invoiceReference) {
    this.invoiceReference = invoiceReference;
    return this;
  }

  public IntentBuilder customerReference(Reference customerReference) {
    this.customerReference = customerReference;
    return this;
  }

  public IntentBuilder expiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  public IntentBuilder metadata(PaymentMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public IntentBuilder purpose(PaymentPurpose purpose) {
    this.purpose = purpose;
    return this;
  }

  public IntentBuilder idempotencyKey(IdempotencyKey idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
    return this;
  }

  public PaymentIntent build() {
    return new PaymentIntent(
        amount, invoiceReference, customerReference, expiresAt, metadata, purpose, idempotencyKey);
  }
}
