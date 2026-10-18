package io.genfin.stripe.http;

import com.stripe.net.HttpClient;
import com.stripe.net.HttpURLConnectionClient;
import io.genfin.providerapi.port.http.HttpClientFactory;

/**
 * Default {@link HttpClientFactory} for Stripe — builds the same {@link HttpURLConnectionClient}
 * the SDK would otherwise construct implicitly. Exists so tests can supply a fake {@link
 * HttpClient} instead.
 */
public final class StripeHttpClientFactory implements HttpClientFactory<HttpClient> {

  @Override
  public HttpClient create() {
    return new HttpURLConnectionClient();
  }
}
