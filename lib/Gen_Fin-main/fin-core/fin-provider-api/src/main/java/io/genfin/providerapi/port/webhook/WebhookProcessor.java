package io.genfin.providerapi.port.webhook;

import io.genfin.api.port.spi.Extension;
import io.genfin.providerapi.webhook.WebhookEvent;

/**
 * Reacts to a parsed {@link WebhookEvent}. Applications implement and register this — never a
 * provider concern.
 */
public interface WebhookProcessor extends Extension {

  void process(WebhookEvent event);
}
