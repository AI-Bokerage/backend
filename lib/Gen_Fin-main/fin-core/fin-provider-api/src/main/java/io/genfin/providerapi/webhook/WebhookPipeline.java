package io.genfin.providerapi.webhook;

import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.exception.InvalidWebhookSignatureException;
import io.genfin.providerapi.port.webhook.WebhookParser;
import io.genfin.providerapi.port.webhook.WebhookProcessor;
import io.genfin.providerapi.port.webhook.WebhookReplayProtection;
import io.genfin.providerapi.port.webhook.WebhookVerifier;
import java.util.Optional;

/**
 * Composes verify → replay-check → parse → process into the one call a controller/handler needs.
 */
public final class WebhookPipeline {

  private final WebhookVerifier verifier;
  private final WebhookParser parser;
  private final WebhookReplayProtection replayProtection;
  private final WebhookProcessor processor;

  public WebhookPipeline(
      WebhookVerifier verifier,
      WebhookParser parser,
      WebhookReplayProtection replayProtection,
      WebhookProcessor processor) {
    this.verifier = verifier;
    this.parser = parser;
    this.replayProtection = replayProtection;
    this.processor = processor;
  }

  public Optional<WebhookEvent> handle(
      WebhookPayload payload, WebhookSignature signature, Credential credential) {
    if (!verifier.verify(payload, signature, credential)) {
      throw new InvalidWebhookSignatureException("Webhook signature verification failed.");
    }
    WebhookEvent event = parser.parse(payload);
    if (replayProtection.isReplay(event.eventId())) {
      return Optional.empty();
    }
    replayProtection.markProcessed(event.eventId());
    processor.process(event);
    return Optional.of(event);
  }
}
