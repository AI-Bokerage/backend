package io.genfin.providerapi.checkout;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.payment.metadata.PaymentMetadata;
import java.net.URI;
import java.util.Optional;

/**
 * {@code redirectUrl} is absent for {@link CheckoutMode#EMBEDDED}/{@link CheckoutMode#SDK}
 * sessions.
 */
public record CheckoutSession(
    String id, CheckoutMode mode, URI redirectUrl, Money amount, PaymentMetadata metadata)
    implements ValueObject {

  public CheckoutSession {
    Validate.notBlank(id, "id must not be blank.");
    Validate.notNull(mode, "mode must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    if (metadata == null) {
      metadata = PaymentMetadata.empty();
    }
  }

  public Optional<URI> redirect() {
    return Optional.ofNullable(redirectUrl);
  }
}
