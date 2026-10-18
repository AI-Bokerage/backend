package io.genfin.razorpay.mapper;

import com.razorpay.Entity;
import io.genfin.payment.gateway.GatewayResponse;
import io.genfin.payment.metadata.PaymentMetadata;

/**
 * Maps Razorpay's own entity objects onto the engine's provider-neutral {@link GatewayResponse}.
 * Razorpay types never leak past this class.
 */
public final class RazorpayResponseMapper {

  private RazorpayResponseMapper() {}

  public static GatewayResponse fromEntity(Entity entity) {
    String id = entity.get("id");
    return GatewayResponse.success(id, PaymentMetadata.empty());
  }
}
