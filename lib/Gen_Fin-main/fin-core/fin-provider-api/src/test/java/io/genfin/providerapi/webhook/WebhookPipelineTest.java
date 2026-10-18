package io.genfin.providerapi.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.exception.InvalidWebhookSignatureException;
import io.genfin.providerapi.internal.webhook.InMemoryReplayProtection;
import io.genfin.providerapi.port.webhook.WebhookParser;
import io.genfin.providerapi.port.webhook.WebhookProcessor;
import io.genfin.providerapi.port.webhook.WebhookReplayProtection;
import io.genfin.providerapi.port.webhook.WebhookVerifier;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WebhookPipelineTest {

  private static final WebhookVerifier ALWAYS_VALID = (payload, signature, credential) -> true;
  private static final WebhookVerifier ALWAYS_INVALID = (payload, signature, credential) -> false;
  private static final WebhookParser ECHO_PARSER =
      payload -> new WebhookEvent("evt-1", "payment.captured", null, Instant.EPOCH);

  @Test
  void invalidSignatureThrows() {
    WebhookPipeline pipeline =
        new WebhookPipeline(
            ALWAYS_INVALID, ECHO_PARSER, new InMemoryReplayProtection(), event -> {});

    assertThatThrownBy(
            () ->
                pipeline.handle(
                    new WebhookPayload("{}", Map.of()),
                    new WebhookSignature("sig", "sha256"),
                    Credential.hmac("s")))
        .isInstanceOf(InvalidWebhookSignatureException.class);
  }

  @Test
  void validSignatureParsesAndProcessesExactlyOnce() {
    AtomicInteger processedCount = new AtomicInteger();
    WebhookProcessor countingProcessor = event -> processedCount.incrementAndGet();
    WebhookReplayProtection replayProtection = new InMemoryReplayProtection();
    WebhookPipeline pipeline =
        new WebhookPipeline(ALWAYS_VALID, ECHO_PARSER, replayProtection, countingProcessor);
    WebhookPayload payload = new WebhookPayload("{}", Map.of());
    WebhookSignature signature = new WebhookSignature("sig", "sha256");
    Credential credential = Credential.hmac("s");

    var first = pipeline.handle(payload, signature, credential);
    var replay = pipeline.handle(payload, signature, credential);

    assertThat(first).isPresent();
    assertThat(replay).isEmpty();
    assertThat(processedCount.get()).isEqualTo(1);
  }
}
