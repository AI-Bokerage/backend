package io.genfin.razorpay.webhook;

import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import io.genfin.providerapi.auth.Credential;
import io.genfin.providerapi.exception.InvalidWebhookSignatureException;
import io.genfin.providerapi.port.webhook.WebhookVerifier;
import io.genfin.providerapi.webhook.WebhookPayload;
import io.genfin.providerapi.webhook.WebhookSignature;

/**
 * Delegates to Razorpay's own {@link Utils#verifyWebhookSignature} HMAC-SHA256 check — the SDK's
 * contract already matches our generic shape exactly. No timestamp-tolerance window is configured
 * here: {@link WebhookPayload} carries no timestamp field for either provider module, and Stripe's
 * verifier is equally a plain HMAC compare, so this stays at parity rather than inventing a
 * tolerance knob only one provider could honor.
 */
public final class RazorpayWebhookVerifier implements WebhookVerifier {

  @Override
  public boolean verify(WebhookPayload payload, WebhookSignature signature, Credential credential) {
    String secret = credential.attributes().get("secret");
    if (secret == null) {
      return false;
    }
    try {
      return Utils.verifyWebhookSignature(payload.rawBody(), signature.value(), secret);
    } catch (RazorpayException e) {
      throw new InvalidWebhookSignatureException(
          "Razorpay webhook signature verification failed: " + e.getMessage(), e);
    }
  }
}
