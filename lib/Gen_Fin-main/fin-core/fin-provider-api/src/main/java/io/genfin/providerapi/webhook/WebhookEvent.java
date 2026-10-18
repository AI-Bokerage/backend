package io.genfin.providerapi.webhook;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.payment.metadata.PaymentMetadata;
import java.time.Instant;

/**
 * The generic, parsed representation of a provider webhook — provider JSON never leaks past the
 * parser.
 */
public record WebhookEvent(
    String eventId, String eventType, PaymentMetadata data, Instant occurredAt)
    implements ValueObject {

  public WebhookEvent {
    Validate.notBlank(eventId, "eventId must not be blank.");
    Validate.notBlank(eventType, "eventType must not be blank.");
    if (data == null) {
      data = PaymentMetadata.empty();
    }
    Validate.notNull(occurredAt, "occurredAt must not be null.");
  }
}
