package io.genfin.providerapi.port.webhook;

import io.genfin.api.port.spi.Extension;

/**
 * Deduplicates webhook deliveries by event id. Contract only — applications decide durable storage.
 */
public interface WebhookReplayProtection extends Extension {

  boolean isReplay(String eventId);

  void markProcessed(String eventId);
}
