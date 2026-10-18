package io.genfin.razorpay.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.razorpay.Utils;
import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.webhook.WebhookPayload;
import io.genfin.providerapi.webhook.WebhookSignature;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RazorpayWebhookVerifierTest {

  @Test
  void validSignatureVerifiesSuccessfully() throws Exception {
    String secret = "webhook_secret";
    String body = "{\"event\":\"payment.captured\"}";
    String signature = Utils.getHash(body, secret);

    boolean valid =
        new RazorpayWebhookVerifier()
            .verify(
                new WebhookPayload(body, Map.of()),
                new WebhookSignature(signature, "sha256"),
                Credential.hmac(secret));

    assertThat(valid).isTrue();
  }

  @Test
  void missingSecretFailsVerification() {
    boolean valid =
        new RazorpayWebhookVerifier()
            .verify(
                new WebhookPayload("{}", Map.of()),
                new WebhookSignature("sig", "sha256"),
                Credential.apiKey("no-secret"));

    assertThat(valid).isFalse();
  }
}
