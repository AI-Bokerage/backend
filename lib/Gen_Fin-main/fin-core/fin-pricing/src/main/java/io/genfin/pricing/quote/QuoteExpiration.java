package io.genfin.pricing.quote;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.time.Duration;
import java.time.Instant;

/**
 * The point in time a {@link Quote} stops being a valid commercial offer. How long a quote stays
 * valid is a {@link io.genfin.pricing.port.quote.QuotePolicy} decision - this type only carries the
 * resolved instant, mirroring {@code io.genfin.pricing.credit.Credit}'s expiry check.
 */
public record QuoteExpiration(Instant expiresAt) implements ValueObject {

  public QuoteExpiration {
    Validate.notNull(expiresAt, "expiresAt must not be null.");
  }

  public static QuoteExpiration at(Instant expiresAt) {
    return new QuoteExpiration(expiresAt);
  }

  /** Expires {@code validity} from now. */
  public static QuoteExpiration after(Duration validity) {
    Validate.notNull(validity, "validity must not be null.");
    return new QuoteExpiration(Instant.now().plus(validity));
  }

  /** Whether this expiration has passed as of {@code instant}. */
  public boolean isExpiredAt(Instant instant) {
    Validate.notNull(instant, "instant must not be null.");
    return !instant.isBefore(expiresAt);
  }
}
