package io.genfin.stripe.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.payment.failure.FailureCategory;
import org.junit.jupiter.api.Test;

class StripeErrorMappingTest {

  @Test
  void genericMapAlwaysReturnsProviderErrorCategory() {
    var reason = new StripeErrorMapping().map("card_declined", "Your card was declined.");

    assertThat(reason.category()).isEqualTo(FailureCategory.PROVIDER_ERROR);
    assertThat(reason.message()).isEqualTo("Your card was declined.");
  }
}
