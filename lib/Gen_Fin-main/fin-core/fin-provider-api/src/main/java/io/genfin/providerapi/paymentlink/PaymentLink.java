package io.genfin.providerapi.paymentlink;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.payment.metadata.PaymentMetadata;
import java.net.URI;
import java.time.Instant;

public record PaymentLink(
    String id,
    URI url,
    Money amount,
    Instant expiresAt,
    PaymentLinkStatus status,
    PaymentMetadata metadata)
    implements ValueObject {

  public PaymentLink {
    Validate.notBlank(id, "id must not be blank.");
    Validate.notNull(url, "url must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    Validate.notNull(status, "status must not be null.");
    if (metadata == null) {
      metadata = PaymentMetadata.empty();
    }
  }
}
