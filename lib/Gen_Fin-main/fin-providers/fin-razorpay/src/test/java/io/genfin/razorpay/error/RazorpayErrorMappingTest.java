package io.genfin.razorpay.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.razorpay.RazorpayException;
import io.genfin.payment.failure.FailureCategory;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class RazorpayErrorMappingTest {

  private final RazorpayErrorMapping mapping = new RazorpayErrorMapping();

  @Test
  void authenticationFailureMapsToProviderErrorNonRetryable() {
    var reason = mapping.mapException(new RazorpayException("bad auth", 401));

    assertThat(reason.category()).isEqualTo(FailureCategory.PROVIDER_ERROR);
    assertThat(reason.retryRecommendation().name()).isEqualTo("DO_NOT_RETRY");
  }

  @Test
  void rateLimitMapsToRetryWithBackoff() {
    var reason = mapping.mapException(new RazorpayException("too many requests", 429));

    assertThat(reason.retryRecommendation().name()).isEqualTo("RETRY_WITH_BACKOFF");
  }

  @Test
  void insufficientFundsReasonMapsToInsufficientFundsCategory() {
    JSONObject errorBody = new JSONObject().put("reason", "insufficient_funds");
    var reason = mapping.mapException(new RazorpayException("declined", errorBody, 400));

    assertThat(reason.category()).isEqualTo(FailureCategory.INSUFFICIENT_FUNDS);
  }

  @Test
  void genericMapAlwaysReturnsProviderErrorCategory() {
    assertThat(mapping.map("CODE", "message").category()).isEqualTo(FailureCategory.PROVIDER_ERROR);
  }
}
