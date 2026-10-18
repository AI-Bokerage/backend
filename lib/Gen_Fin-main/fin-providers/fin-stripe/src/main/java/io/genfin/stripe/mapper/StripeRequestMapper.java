package io.genfin.stripe.mapper;

import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import io.genfin.money.money.Money;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.providerapi.capability.CaptureMode;
import io.genfin.stripe.config.ConfirmationMethod;
import java.math.BigDecimal;

/**
 * Maps the engine's provider-neutral {@link GatewayRequest} onto Stripe's own request param
 * builders. No engine type leaks past this class.
 */
public final class StripeRequestMapper {

  private StripeRequestMapper() {}

  public static PaymentIntentCreateParams toCreateParams(
      GatewayRequest request, CaptureMode captureMode, ConfirmationMethod confirmationMethod) {
    return toCreateParams(request, captureMode, confirmationMethod, null);
  }

  public static PaymentIntentCreateParams toCreateParams(
      GatewayRequest request,
      CaptureMode captureMode,
      ConfirmationMethod confirmationMethod,
      String statementDescriptor) {
    boolean automatic = captureMode == CaptureMode.AUTOMATIC;
    PaymentIntentCreateParams.Builder builder =
        PaymentIntentCreateParams.builder()
            .setAmount(toMinorUnits(request.amount()))
            .setCurrency(request.amount().currency().code().toLowerCase(java.util.Locale.ROOT))
            .setCaptureMethod(
                automatic
                    ? PaymentIntentCreateParams.CaptureMethod.AUTOMATIC
                    : PaymentIntentCreateParams.CaptureMethod.MANUAL)
            .setConfirmationMethod(
                confirmationMethod == ConfirmationMethod.AUTOMATIC
                    ? PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC
                    : PaymentIntentCreateParams.ConfirmationMethod.MANUAL)
            .setConfirm(automatic);
    if (statementDescriptor != null) {
      builder.setStatementDescriptor(statementDescriptor);
    }
    request.metadata().values().forEach(builder::putMetadata);
    return builder.build();
  }

  public static PaymentIntentCaptureParams toCaptureParams(GatewayRequest request) {
    return PaymentIntentCaptureParams.builder()
        .setAmountToCapture(toMinorUnits(request.amount()))
        .build();
  }

  public static RefundCreateParams toRefundParams(String paymentIntentId, GatewayRequest request) {
    return RefundCreateParams.builder()
        .setPaymentIntent(paymentIntentId)
        .setAmount(toMinorUnits(request.amount()))
        .build();
  }

  private static long toMinorUnits(Money amount) {
    BigDecimal minorUnits = amount.amount().movePointRight(amount.currency().fractionDigits());
    return minorUnits.longValueExact();
  }
}
