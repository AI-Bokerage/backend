package io.genfin.stripe.error;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;
import io.genfin.payment.failure.FailureCategory;
import io.genfin.payment.failure.FailureReason;
import io.genfin.payment.failure.RecoveryAction;
import io.genfin.payment.failure.RetryRecommendation;
import io.genfin.payment.port.failure.ErrorMapping;

/**
 * Translates Stripe's own exception hierarchy into the engine's generic {@link FailureReason} — no
 * Stripe types leak past this class.
 */
public final class StripeErrorMapping implements ErrorMapping {

  @Override
  public FailureReason map(String providerErrorCode, String providerMessage) {
    return new FailureReason(
        FailureCategory.PROVIDER_ERROR,
        providerMessage,
        RetryRecommendation.RETRY_WITH_BACKOFF,
        RecoveryAction.RETRY_LATER);
  }

  public FailureReason mapException(StripeException exception) {
    if (exception instanceof CardException cardException) {
      return new FailureReason(
          FailureCategory.CARD_DECLINED,
          cardException.getMessage(),
          RetryRecommendation.RETRY_WITH_DIFFERENT_METHOD,
          RecoveryAction.UPDATE_PAYMENT_METHOD);
    }
    if (exception instanceof AuthenticationException) {
      return new FailureReason(
          FailureCategory.PROVIDER_ERROR,
          exception.getMessage(),
          RetryRecommendation.DO_NOT_RETRY,
          RecoveryAction.CONTACT_SUPPORT);
    }
    if (exception instanceof RateLimitException) {
      return new FailureReason(
          FailureCategory.PROVIDER_ERROR,
          exception.getMessage(),
          RetryRecommendation.RETRY_WITH_BACKOFF,
          RecoveryAction.RETRY_LATER);
    }
    if (exception instanceof ApiConnectionException) {
      return new FailureReason(
          FailureCategory.NETWORK_ERROR,
          exception.getMessage(),
          RetryRecommendation.RETRY_WITH_BACKOFF,
          RecoveryAction.RETRY_LATER);
    }
    return new FailureReason(
        FailureCategory.UNKNOWN,
        exception.getMessage(),
        RetryRecommendation.DO_NOT_RETRY,
        RecoveryAction.CONTACT_SUPPORT);
  }

  /**
   * Rejects a request up front for a currency Stripe does not support, instead of letting Stripe's
   * own API error surface raw.
   */
  public FailureReason unsupportedCurrency(String currencyCode) {
    return new FailureReason(
        FailureCategory.PROVIDER_ERROR,
        "Stripe does not support currency '" + currencyCode + "'.",
        RetryRecommendation.DO_NOT_RETRY,
        RecoveryAction.NONE);
  }

  /**
   * Rejects a request up front for a capture-mode/payment-method combination Stripe cannot honor,
   * instead of letting Stripe's own API error surface raw.
   */
  public FailureReason unsupportedCaptureMode(String captureMode, String methodCode) {
    return new FailureReason(
        FailureCategory.PROVIDER_ERROR,
        "Stripe does not support capture mode '"
            + captureMode
            + "' for method '"
            + methodCode
            + "'.",
        RetryRecommendation.DO_NOT_RETRY,
        RecoveryAction.NONE);
  }
}
