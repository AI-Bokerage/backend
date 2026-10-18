package io.genfin.stripe.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.webhook.WebhookPayload;
import io.genfin.providerapi.webhook.WebhookSignature;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class StripeWebhookVerifierTest {

  private static String hmacHex(String secret, String data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
    byte[] raw = mac.doFinal(data.getBytes());
    StringBuilder hex = new StringBuilder();
    for (byte b : raw) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

  private static String stripeSignatureHeader(String secret, String timestamp, String body)
      throws Exception {
    String v1 = hmacHex(secret, timestamp + "." + body);
    return "t=" + timestamp + ",v1=" + v1;
  }

  @Test
  void validSignatureVerifiesSuccessfully() throws Exception {
    String secret = "whsec_test";
    String body = "{\"id\":\"evt_1\"}";
    String header = stripeSignatureHeader(secret, "1614556800", body);

    boolean valid =
        new StripeWebhookVerifier()
            .verify(
                new WebhookPayload(body, Map.of()),
                new WebhookSignature(header, "sha256"),
                Credential.hmac(secret));

    assertThat(valid).isTrue();
  }

  @Test
  void tamperedPayloadFailsVerification() throws Exception {
    String secret = "whsec_test";
    String header = stripeSignatureHeader(secret, "1614556800", "{\"id\":\"evt_1\"}");

    boolean valid =
        new StripeWebhookVerifier()
            .verify(
                new WebhookPayload("{\"id\":\"evt_2\"}", Map.of()),
                new WebhookSignature(header, "sha256"),
                Credential.hmac(secret));

    assertThat(valid).isFalse();
  }

  @Test
  void malformedSignatureHeaderFailsVerification() {
    boolean valid =
        new StripeWebhookVerifier()
            .verify(
                new WebhookPayload("{}", Map.of()),
                new WebhookSignature("not-a-valid-header", "sha256"),
                Credential.hmac("whsec_test"));

    assertThat(valid).isFalse();
  }

  @Test
  void missingSecretFailsVerification() {
    boolean valid =
        new StripeWebhookVerifier()
            .verify(
                new WebhookPayload("{}", Map.of()),
                new WebhookSignature("t=1614556800,v1=sig", "sha256"),
                Credential.apiKey("no-secret-here"));

    assertThat(valid).isFalse();
  }
}
