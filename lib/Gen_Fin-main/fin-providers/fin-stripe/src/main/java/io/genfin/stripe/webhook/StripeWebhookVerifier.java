package io.genfin.stripe.webhook;

import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.port.webhook.WebhookVerifier;
import io.genfin.providerapi.webhook.WebhookPayload;
import io.genfin.providerapi.webhook.WebhookSignature;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies Stripe's actual webhook signature scheme: the {@code Stripe-Signature} header value
 * (passed as {@link WebhookSignature#value()}) is a comma-separated {@code t=<timestamp>,v1=<hex>}
 * pair — Stripe signs {@code "<timestamp>.<rawBody>"}, not the raw body alone. Reimplemented with
 * plain JDK crypto rather than calling into the SDK's own header-parsing utility, so this class
 * stays independently testable.
 */
public final class StripeWebhookVerifier implements WebhookVerifier {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  @Override
  public boolean verify(WebhookPayload payload, WebhookSignature signature, Credential credential) {
    String secret = credential.attributes().get("secret");
    if (secret == null) {
      return false;
    }
    Map<String, String> parts = parseSignatureHeader(signature.value());
    String timestamp = parts.get("t");
    String providedHash = parts.get("v1");
    if (timestamp == null || providedHash == null) {
      return false;
    }
    String signedPayload = timestamp + "." + payload.rawBody();
    String expected = hmacHex(secret, signedPayload);
    return java.security.MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), providedHash.getBytes(StandardCharsets.UTF_8));
  }

  private static Map<String, String> parseSignatureHeader(String header) {
    Map<String, String> parts = new HashMap<>();
    for (String element : header.split(",")) {
      int separator = element.indexOf('=');
      if (separator > 0) {
        parts.put(element.substring(0, separator).trim(), element.substring(separator + 1).trim());
      }
    }
    return parts;
  }

  private static String hmacHex(String secret, String data) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
      byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(rawHmac.length * 2);
      for (byte b : rawHmac) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }
}
