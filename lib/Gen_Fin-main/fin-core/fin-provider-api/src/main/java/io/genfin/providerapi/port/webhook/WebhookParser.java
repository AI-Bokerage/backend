package io.genfin.providerapi.port.webhook;

import io.genfin.api.port.spi.Extension;
import io.genfin.providerapi.webhook.WebhookEvent;
import io.genfin.providerapi.webhook.WebhookPayload;

/**
 * Turns a verified {@link WebhookPayload} into a generic {@link WebhookEvent}. Each provider module
 * implements this.
 */
public interface WebhookParser extends Extension {

  WebhookEvent parse(WebhookPayload payload);
}
