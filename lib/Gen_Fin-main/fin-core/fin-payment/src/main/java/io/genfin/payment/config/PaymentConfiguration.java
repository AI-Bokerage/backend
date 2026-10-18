package io.genfin.payment.config;

import io.genfin.api.validation.Validate;
import io.genfin.payment.port.authorization.AuthorizationStrategy;
import io.genfin.payment.port.authorization.CaptureStrategy;
import io.genfin.payment.port.gateway.GatewayRegistry;
import io.genfin.payment.port.gateway.GatewaySelector;
import io.genfin.payment.port.lifecycle.LifecycleProvider;
import io.genfin.payment.port.method.PaymentMethodRegistry;
import java.time.Duration;

/** The full, explicit policy set for a Payment-engine deployment. */
public final class PaymentConfiguration {

  private final LifecycleProvider lifecycleProvider;
  private final GatewayRegistry gatewayRegistry;
  private final GatewaySelector gatewaySelector;
  private final PaymentMethodRegistry methodRegistry;
  private final CaptureStrategy captureStrategy;
  private final AuthorizationStrategy authorizationStrategy;
  private final boolean autoSettle;
  private final Duration sessionTimeout;
  private final int maxRetryAttempts;

  private PaymentConfiguration(Builder builder) {
    this.lifecycleProvider =
        Validate.notNull(builder.lifecycleProvider, "lifecycleProvider must not be null.");
    this.gatewayRegistry =
        Validate.notNull(builder.gatewayRegistry, "gatewayRegistry must not be null.");
    this.gatewaySelector =
        Validate.notNull(builder.gatewaySelector, "gatewaySelector must not be null.");
    this.methodRegistry =
        Validate.notNull(builder.methodRegistry, "methodRegistry must not be null.");
    this.captureStrategy =
        Validate.notNull(builder.captureStrategy, "captureStrategy must not be null.");
    this.authorizationStrategy =
        Validate.notNull(builder.authorizationStrategy, "authorizationStrategy must not be null.");
    this.autoSettle = builder.autoSettle;
    this.sessionTimeout =
        Validate.notNull(builder.sessionTimeout, "sessionTimeout must not be null.");
    this.maxRetryAttempts =
        Validate.nonNegative(builder.maxRetryAttempts, "maxRetryAttempts must not be negative.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public LifecycleProvider lifecycleProvider() {
    return lifecycleProvider;
  }

  public GatewayRegistry gatewayRegistry() {
    return gatewayRegistry;
  }

  public GatewaySelector gatewaySelector() {
    return gatewaySelector;
  }

  public PaymentMethodRegistry methodRegistry() {
    return methodRegistry;
  }

  public CaptureStrategy captureStrategy() {
    return captureStrategy;
  }

  public AuthorizationStrategy authorizationStrategy() {
    return authorizationStrategy;
  }

  public boolean autoSettle() {
    return autoSettle;
  }

  public Duration sessionTimeout() {
    return sessionTimeout;
  }

  /** Placeholder for a future retry policy — currently just a bound on attempt count. */
  public int maxRetryAttempts() {
    return maxRetryAttempts;
  }

  public static final class Builder {

    private LifecycleProvider lifecycleProvider;
    private GatewayRegistry gatewayRegistry;
    private GatewaySelector gatewaySelector;
    private PaymentMethodRegistry methodRegistry;
    private CaptureStrategy captureStrategy;
    private AuthorizationStrategy authorizationStrategy;
    private boolean autoSettle = true;
    private Duration sessionTimeout = Duration.ofMinutes(30);
    private int maxRetryAttempts = 3;

    public Builder lifecycleProvider(LifecycleProvider lifecycleProvider) {
      this.lifecycleProvider = lifecycleProvider;
      return this;
    }

    public Builder gatewayRegistry(GatewayRegistry gatewayRegistry) {
      this.gatewayRegistry = gatewayRegistry;
      return this;
    }

    public Builder gatewaySelector(GatewaySelector gatewaySelector) {
      this.gatewaySelector = gatewaySelector;
      return this;
    }

    public Builder methodRegistry(PaymentMethodRegistry methodRegistry) {
      this.methodRegistry = methodRegistry;
      return this;
    }

    public Builder captureStrategy(CaptureStrategy captureStrategy) {
      this.captureStrategy = captureStrategy;
      return this;
    }

    public Builder authorizationStrategy(AuthorizationStrategy authorizationStrategy) {
      this.authorizationStrategy = authorizationStrategy;
      return this;
    }

    public Builder autoSettle(boolean autoSettle) {
      this.autoSettle = autoSettle;
      return this;
    }

    public Builder sessionTimeout(Duration sessionTimeout) {
      this.sessionTimeout = sessionTimeout;
      return this;
    }

    public Builder maxRetryAttempts(int maxRetryAttempts) {
      this.maxRetryAttempts = maxRetryAttempts;
      return this;
    }

    public PaymentConfiguration build() {
      return new PaymentConfiguration(this);
    }
  }
}
