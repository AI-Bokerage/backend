package io.genfin.providerapi.webhook;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import java.util.Map;

/**
 * The raw inbound webhook body/headers — the engine never assumes an HTTP framework carries these.
 */
public record WebhookPayload(String rawBody, Map<String, String> headers) implements ValueObject {

  public WebhookPayload {
    Validate.notNull(rawBody, "rawBody must not be null.");
    headers = Map.copyOf(headers);
  }
}
