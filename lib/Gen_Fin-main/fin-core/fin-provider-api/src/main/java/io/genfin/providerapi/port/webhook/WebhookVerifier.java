package io.genfin.providerapi.port.webhook;

import io.genfin.api.port.spi.Extension;
import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.webhook.WebhookPayload;
import io.genfin.providerapi.webhook.WebhookSignature;

/**
 * Verifies webhook authenticity. Each provider module implements this using its own signing scheme.
 */
public interface WebhookVerifier extends Extension {

  boolean verify(WebhookPayload payload, WebhookSignature signature, Credential credential);
}
