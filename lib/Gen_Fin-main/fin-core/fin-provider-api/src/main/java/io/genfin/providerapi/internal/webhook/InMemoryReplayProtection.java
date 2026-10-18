package io.genfin.providerapi.internal.webhook;

import io.genfin.providerapi.port.webhook.WebhookReplayProtection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A process-local, non-durable default. Real replay protection across restarts is the application's
 * job.
 */
public final class InMemoryReplayProtection implements WebhookReplayProtection {

  private final Set<String> seen = ConcurrentHashMap.newKeySet();

  @Override
  public boolean isReplay(String eventId) {
    return seen.contains(eventId);
  }

  @Override
  public void markProcessed(String eventId) {
    seen.add(eventId);
  }
}
