package io.genfin.stripe.mapper;

import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import io.genfin.payment.gateway.GatewayResponse;
import io.genfin.payment.metadata.PaymentMetadata;

/**
 * Maps Stripe's own response objects onto the engine's provider-neutral {@link GatewayResponse}.
 * Stripe types never leak past this class.
 */
public final class StripeResponseMapper {

  private StripeResponseMapper() {}

  public static GatewayResponse fromPaymentIntent(PaymentIntent paymentIntent) {
    return GatewayResponse.success(paymentIntent.getId(), metadataOf(paymentIntent.getMetadata()));
  }

  public static GatewayResponse fromRefund(Refund refund) {
    return GatewayResponse.success(refund.getId(), metadataOf(refund.getMetadata()));
  }

  private static PaymentMetadata metadataOf(java.util.Map<String, String> raw) {
    return raw == null ? PaymentMetadata.empty() : new PaymentMetadata(raw);
  }
}
