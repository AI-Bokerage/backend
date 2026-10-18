package io.genfin.stripe.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.stripe.net.HttpURLConnectionClient;
import org.junit.jupiter.api.Test;

class StripeHttpClientFactoryTest {

  @Test
  void createsAFreshHttpUrlConnectionClientEachCall() {
    StripeHttpClientFactory factory = new StripeHttpClientFactory();

    var first = factory.create();
    var second = factory.create();

    assertThat(first).isInstanceOf(HttpURLConnectionClient.class);
    assertThat(second).isInstanceOf(HttpURLConnectionClient.class);
    assertThat(first).isNotSameAs(second);
  }
}
