package io.genfin.razorpay.gateway;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import io.genfin.payment.failure.FailureCategory;
import io.genfin.payment.failure.FailureReason;
import io.genfin.payment.failure.RecoveryAction;
import io.genfin.payment.failure.RetryRecommendation;
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
import io.genfin.razorpay.config.RazorpayConfiguration;
import io.genfin.razorpay.currency.RazorpaySupportedCurrencies;
import io.genfin.razorpay.error.RazorpayErrorMapping;
import io.genfin.razorpay.mapper.RazorpayRequestMapper;
import io.genfin.razorpay.mapper.RazorpayResponseMapper;
import java.time.Instant;
import java.util.Set;

/**
 * Implements {@link PaymentGateway} using the official Razorpay Java SDK. {@code authorize} creates
 * a Razorpay Order (the closest equivalent to an unconfirmed Stripe PaymentIntent, since Razorpay
 * Payments are normally created client-side during checkout); its returned {@code gatewayReference}
 * is the order id. {@code capture}/{@code refund} act on an existing Razorpay *payment* id, which
 * the caller must supply via {@code GatewayRequest.metadata()}'s {@code "gatewayReference"} key
 * (the same convention {@code fin-stripe} uses) — obtained from the checkout callback or a webhook,
 * since this module never talks to Razorpay Checkout itself. Razorpay has no API to void an unpaid
 * order, so {@link #voidAuthorization} always fails — reflected honestly in {@link
 * #capabilities()}. Two Stripe-side knobs have no Razorpay counterpart and are deliberately not
 * force-fitted here: an API-version pin (razorpay-java 1.4.10 always calls Razorpay's {@code v1}
 * internally with no override point) and a statement descriptor (Razorpay orders/payments have no
 * bank-statement-text field; the closest concept, a payment's {@code description}, is customer-
 * facing checkout copy, not a statement descriptor).
 */
public final class RazorpayGateway implements PaymentGateway {

  private static final ProviderFeatureMatrix FEATURE_MATRIX =
      new ProviderFeatureMatrix(
          Set.of(CaptureMode.AUTOMATIC, CaptureMode.MANUAL),
          Set.of(
              StandardPaymentMethodType.CARD.code(),
              StandardPaymentMethodType.UPI.code(),
              StandardPaymentMethodType.WALLET.code()),
          RazorpaySupportedCurrencies.CODES);

  private final RazorpayErrorMapping errorMapping;
  private final RazorpayClient client;
  private final RazorpayConfiguration configuration;
  private final RazorpayRetryExecutor retryExecutor;

  public RazorpayGateway(RazorpayConfiguration configuration) {
    this.errorMapping = new RazorpayErrorMapping();
    this.configuration = configuration;
    this.retryExecutor = new RazorpayRetryExecutor(configuration.base().retryPolicy());
    try {
      this.client = new RazorpayClient(configuration.keyId(), configuration.keySecret());
    } catch (RazorpayException e) {
      throw new IllegalStateException("Failed to initialize Razorpay client", e);
    }
    // configuration.base().timeout() is intentionally not applied: razorpay-java 1.4.10 builds one
    // process-wide OkHttpClient with a hardcoded 60s read/write timeout the first time any
    // RazorpayClient is constructed (ApiUtils#createHttpClientInstance) and exposes no API to
    // override it afterwards, so there is no per-instance seam to wire this into.
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
      // GatewayRequest.idempotencyKey() is intentionally not forwarded: razorpay-java 1.4.10 does
      // have RazorpayClient#addHeaders, but it writes into one static, process-wide header map
      // (ApiUtils#headers) shared by every client/request in the JVM rather than a per-call
      // option, so using it to carry a per-request idempotency key would leak that key onto
      // unrelated concurrent requests. There is no per-request seam to send it safely.
      var order =
          retryExecutor.execute(
              () ->
                  client.orders.create(
                      RazorpayRequestMapper.toOrderCreateRequest(
                          request, configuration.captureMode())));
      return RazorpayResponseMapper.fromEntity(order);
    } catch (RazorpayException e) {
      return GatewayResponse.failure(errorMapping.mapException(e));
    }
  }

  @Override
  public GatewayResponse capture(GatewayRequest request) {
    try {
      String paymentId = requireGatewayReference(request);
      var payment =
          retryExecutor.execute(
              () ->
                  client.payments.capture(
                      paymentId, RazorpayRequestMapper.toCaptureRequest(request)));
      return RazorpayResponseMapper.fromEntity(payment);
    } catch (RazorpayException e) {
      return GatewayResponse.failure(errorMapping.mapException(e));
    }
  }

  @Override
  public GatewayResponse voidAuthorization(GatewayRequest request) {
    return GatewayResponse.failure(
        new FailureReason(
            FailureCategory.UNKNOWN,
            "Razorpay has no API to void an unpaid order/payment.",
            RetryRecommendation.DO_NOT_RETRY,
            RecoveryAction.NONE));
  }

  @Override
  public GatewayResponse refund(GatewayRequest request) {
    try {
      String paymentId = requireGatewayReference(request);
      var refund =
          retryExecutor.execute(
              () ->
                  client.payments.refund(
                      paymentId, RazorpayRequestMapper.toRefundRequest(request)));
      return RazorpayResponseMapper.fromEntity(refund);
    } catch (RazorpayException e) {
      return GatewayResponse.failure(errorMapping.mapException(e));
    }
  }

  @Override
  public GatewayCapabilities capabilities() {
    return new GatewayCapabilities(
        true,
        false,
        true,
        false,
        true,
        Set.of(
            StandardPaymentMethodType.CARD,
            StandardPaymentMethodType.UPI,
            StandardPaymentMethodType.WALLET));
  }

  @Override
  public GatewayHealth health() {
    return GatewayHealth.up(Instant.now());
  }

  @Override
  public PaymentProvider provider() {
    return new PaymentProvider(
        "razorpay", "Razorpay", new ProviderCapabilities(Set.of("INR"), Set.of("IN"), true, true));
  }

  private String requireGatewayReference(GatewayRequest request) {
    return request
        .metadata()
        .find("gatewayReference")
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "GatewayRequest.metadata() must carry 'gatewayReference' for this operation."));
  }
}
