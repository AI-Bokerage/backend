package io.genfin.providerapi.port.http;

/**
 * Creates the HTTP client a provider's SDK uses for outbound calls, so a gateway constructor can
 * accept one instead of the SDK silently constructing its own default — letting tests inject a
 * fake. {@code T} is the provider SDK's own client type; this port never assumes what it is.
 */
public interface HttpClientFactory<T> {

  T create();
}
