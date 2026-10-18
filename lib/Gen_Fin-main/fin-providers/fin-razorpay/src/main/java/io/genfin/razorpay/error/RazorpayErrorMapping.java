package io.genfin.razorpay.error;

import com.razorpay.RazorpayException;
import io.genfin.payment.failure.FailureCategory;
import io.genfin.payment.failure.FailureReason;
import io.genfin.payment.failure.RecoveryAction;
import io.genfin.payment.failure.RetryRecommendation;
import io.genfin.payment.port.failure.ErrorMapping;

/**
 * Translates Razorpay's own exception shape into the engine's generic {@link FailureReason} — no
 * Razorpay types leak past this class.
 */
public final class RazorpayErrorMapping implements ErrorMapping {

  private static final int HTTP_UNAUTHORIZED = 401;
  private static final int HTTP_TOO_MANY_REQUESTS = 429;

  @Override
  public FailureReason map(String providerErrorCode, String providerMessage) {
    return new FailureReason(
        FailureCategory.PROVIDER_ERROR,
        providerMessage,
        RetryRecommendation.RETRY_WITH_BACKOFF,
        RecoveryAction.RETRY_LATER);
  }

  public FailureReason mapException(RazorpayException exception) {
    String reason = exception.getReason();
    if (exception.getStatusCode() == HTTP_UNAUTHORIZED) {
      return new FailureReason(
          FailureCategory.PROVIDER_ERROR,
          exception.getMessage(),
          RetryRecommendation.DO_NOT_RETRY,
          RecoveryAction.CONTACT_SUPPORT);
    }
    if (exception.getStatusCode() == HTTP_TOO_MANY_REQUESTS) {
      return new FailureReason(
          FailureCategory.PROVIDER_ERROR,
          exception.getMessage(),
          RetryRecommendation.RETRY_WITH_BACKOFF,
          RecoveryAction.RETRY_LATER);
    }
    if (reason != null && reason.toLowerCase(java.util.Locale.ROOT).contains("insufficient")) {
      return new FailureReason(
          FailureCategory.INSUFFICIENT_FUNDS,
          exception.getMessage(),
          RetryRecommendation.RETRY_WITH_DIFFERENT_METHOD,
          RecoveryAction.UPDATE_PAYMENT_METHOD);
    }
    if (reason != null && reason.toLowerCase(java.util.Locale.ROOT).contains("declined")) {
      return new FailureReason(
          FailureCategory.CARD_DECLINED,
          exception.getMessage(),
          RetryRecommendation.RETRY_WITH_DIFFERENT_METHOD,
          RecoveryAction.UPDATE_PAYMENT_METHOD);
    }
    return new FailureReason(
        FailureCategory.UNKNOWN,
        exception.getMessage(),
        RetryRecommendation.DO_NOT_RETRY,
        RecoveryAction.CONTACT_SUPPORT);
  }

  /**
   * Rejects a request up front for a currency this Razorpay account isn't set up to accept, instead
   * of letting Razorpay's own API error surface raw.
   */
  public FailureReason unsupportedCurrency(String currencyCode) {
    return new FailureReason(
        FailureCategory.PROVIDER_ERROR,
        "Razorpay does not support currency '" + currencyCode + "'.",
        RetryRecommendation.DO_NOT_RETRY,
        RecoveryAction.NONE);
  }

  /**
   * Rejects a request up front for a capture-mode/payment-method combination Razorpay cannot honor,
   * instead of letting Razorpay's own API error surface raw.
   */
  public FailureReason unsupportedCaptureMode(String captureMode, String methodCode) {
    return new FailureReason(
        FailureCategory.PROVIDER_ERROR,
        "Razorpay does not support capture mode '"
            + captureMode
            + "' for method '"
            + methodCode
            + "'.",
        RetryRecommendation.DO_NOT_RETRY,
        RecoveryAction.NONE);
  }
}
