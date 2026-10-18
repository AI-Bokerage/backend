package io.genfin.providerapi.webhook;

import io.genfin.api.validation.Validate;

public record WebhookSignature(String value, String algorithm) {

  public WebhookSignature {
    Validate.notBlank(value, "value must not be blank.");
    Validate.notBlank(algorithm, "algorithm must not be blank.");
  }
}
