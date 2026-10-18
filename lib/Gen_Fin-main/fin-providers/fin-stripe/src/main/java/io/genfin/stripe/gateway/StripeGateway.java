package io.genfin.stripe.gateway;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.ApiResource;
import com.stripe.net.HttpClient;
import com.stripe.net.LiveStripeResponseGetter;
import com.stripe.net.RequestOptions;
import io.genfin.payment.gateway.GatewayCapabilities;
import io.genfin.payment.gateway.GatewayHealth;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.payment.gateway.GatewayResponse;
import io.genfin.payment.gateway.PaymentProvider;
import io.genfin.payment.gateway.ProviderCapabilities;
import io.genfin.payment.method.StandardPaymentMethodType;
import io.genfin.payment.port.gateway.PaymentGateway;
import io.genfin.providerapi.capability.CaptureMode;
import io.genfin.providerapi.capability.ProviderFeatureMatrix;
import io.genfin.providerapi.port.http.HttpClientFactory;
import io.genfin.stripe.config.StripeConfiguration;
import io.genfin.stripe.currency.StripeSupportedCurrencies;
import io.genfin.stripe.error.StripeErrorMapping;
import io.genfin.stripe.http.StripeHttpClientFactory;
import io.genfin.stripe.mapper.StripeRequestMapper;
import io.genfin.stripe.mapper.StripeResponseMapper;
import java.time.Instant;
import java.util.Set;

/**
 * Implements {@link PaymentGateway} using the official Stripe Java SDK. {@code capture}/{@code
 * voidAuthorization}/{@code refund} act on a previously-created PaymentIntent — since {@link
 * GatewayRequest} carries no "existing reference" field (and the Payment Engine's public API is not
 * modified by this phase), the convention is that the caller echoes the {@code gatewayReference}
 * returned by {@code authorize} back into the next request's metadata under the key {@code
 * "gatewayReference"}.
 */
public final class StripeGateway implements PaymentGateway {

  private static final String GATEWAY_REFERENCE_METADATA_KEY = "gatewayReference";

  private static final ProviderFeatureMatrix FEATURE_MATRIX =
      new ProviderFeatureMatrix(
          Set.of(CaptureMode.AUTOMATIC, CaptureMode.MANUAL),
          Set.of(
              StandardPaymentMethodType.CARD.code(),
              StandardPaymentMethodType.WALLET.code(),
              StandardPaymentMethodType.ACH.code()),
          StripeSupportedCurrencies.CODES);

  private final StripeConfiguration configuration;
  private final StripeErrorMapping errorMapping;
  private final StripeRetryExecutor retryExecutor;

  public StripeGateway(StripeConfiguration configuration) {
    this(configuration, new StripeHttpClientFactory());
  }

  public StripeGateway(
      StripeConfiguration configuration, HttpClientFactory<HttpClient> httpClientFactory) {
    this.configuration = configuration;
    this.errorMapping = new StripeErrorMapping();
    this.retryExecutor = new StripeRetryExecutor(configuration.base().retryPolicy());
    // Stripe's static SDK entry points (PaymentIntent.create, ...) always resolve to this
    // process-wide singleton rather than taking an HttpClient per call — this is the SDK's own
    // seam for swapping it out, e.g. with a fake in tests.
    ApiResource.setGlobalResponseGetter(new LiveStripeResponseGetter(httpClientFactory.create()));
  }

  @Override
  public GatewayResponse authorize(GatewayRequest request) {
    String currencyCode = request.amount().currency().code();
    if (!FEATURE_MATRIX.supportsCurrency(currencyCode)) {
      return GatewayResponse.failure(errorMapping.unsupportedCurrency(currencyCode));
    }
    if (!FEATURE_MATRIX.supportsCaptureMode(configuration.captureMode(), request.method().type())) {
      return GatewayResponse.failure(
          errorMapping.unsupportedCaptureMode(
              configuration.captureMode().name(), request.method().type().code()));
    }
    try {
      PaymentIntent created =
          retryExecutor.execute(
              () ->
                  PaymentIntent.create(
                      StripeRequestMapper.toCreateParams(
                          request,
                          configuration.captureMode(),
                          configuration.confirmationMethod(),
                          configuration.statementDescriptor()),
                      requestOptions(request)));
      return StripeResponseMapper.fromPaymentIntent(created);
    } catch (StripeException e) {
      return GatewayResponse.failure(errorMapping.mapException(e));
    }
  }

  @Override
  public GatewayResponse capture(GatewayRequest request) {
    try {
      String paymentIntentId = requireGatewayReference(request);
      PaymentIntent captured =
          retryExecutor.execute(
              () -> {
                PaymentIntent intent =
                    PaymentIntent.retrieve(paymentIntentId, requestOptions(request));
                return intent.capture(
                    StripeRequestMapper.toCaptureParams(request), requestOptions(request));
              });
      return StripeResponseMapper.fromPaymentIntent(captured);
    } catch (StripeException e) {
      return GatewayResponse.failure(errorMapping.mapException(e));
    }
  }

  @Override
  public GatewayResponse voidAuthorization(GatewayRequest request) {
    try {
      String paymentIntentId = requireGatewayReference(request);
      PaymentIntent cancelled =
          retryExecutor.execute(
              () -> {
                PaymentIntent intent =
                    PaymentIntent.retrieve(paymentIntentId, requestOptions(request));
                return intent.cancel(requestOptions(request));
              });
      return StripeResponseMapper.fromPaymentIntent(cancelled);
    } catch (StripeException e) {
      return GatewayResponse.failure(errorMapping.mapException(e));
    }
  }

  @Override
  public GatewayResponse refund(GatewayRequest request) {
    try {
      String paymentIntentId = requireGatewayReference(request);
      Refund refund =
          retryExecutor.execute(
              () ->
                  Refund.create(
                      StripeRequestMapper.toRefundParams(paymentIntentId, request),
                      requestOptions(request)));
      return StripeResponseMapper.fromRefund(refund);
    } catch (StripeException e) {
      return GatewayResponse.failure(errorMapping.mapException(e));
    }
  }

  @Override
  public GatewayCapabilities capabilities() {
    return new GatewayCapabilities(
        true,
        true,
        true,
        true,
        true,
        Set.of(
            StandardPaymentMethodType.CARD,
            StandardPaymentMethodType.WALLET,
            StandardPaymentMethodType.ACH));
  }

  @Override
  public GatewayHealth health() {
    return GatewayHealth.up(Instant.now());
  }

  @Override
  public PaymentProvider provider() {
    return new PaymentProvider(
        "stripe",
        "Stripe",
        new ProviderCapabilities(
            Set.of("USD", "EUR", "GBP"), Set.of("US", "GB", "EU"), true, true));
  }

  private String requireGatewayReference(GatewayRequest request) {
    return request
        .metadata()
        .find(GATEWAY_REFERENCE_METADATA_KEY)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "GatewayRequest.metadata() must carry '"
                        + GATEWAY_REFERENCE_METADATA_KEY
                        + "' for this operation."));
  }

  // Package-private (not private) solely so StripeGatewayTest can assert on the built
  // RequestOptions directly instead of re-deriving them from a live HTTP call.
  RequestOptions requestOptions(GatewayRequest request) {
    int timeoutMillis = Math.toIntExact(configuration.base().timeout().toMillis());
    RequestOptions.RequestOptionsBuilder builder =
        RequestOptions.builder()
            .setApiKey(configuration.secretKey())
            .setIdempotencyKey(request.idempotencyKey().value())
            .setConnectTimeout(timeoutMillis)
            .setReadTimeout(timeoutMillis);
    if (configuration.apiVersion() != null) {
      builder =
          RequestOptions.RequestOptionsBuilder.unsafeSetStripeVersionOverride(
              builder, configuration.apiVersion());
    }
    return builder.build();
  }
}
