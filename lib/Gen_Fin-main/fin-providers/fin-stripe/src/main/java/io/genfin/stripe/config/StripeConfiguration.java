package io.genfin.stripe.config;

import io.genfin.api.validation.Validate;
import io.genfin.providerapi.capability.CaptureMode;
import io.genfin.providerapi.config.ProviderConfiguration;

/** Stripe-specific settings layered on top of the generic {@link ProviderConfiguration}. */
public final class StripeConfiguration {

  private final ProviderConfiguration base;
  private final String webhookSigningSecret;
  private final CaptureMode captureMode;
  private final ConfirmationMethod confirmationMethod;
  private final String apiVersion;
  private final String statementDescriptor;

  private StripeConfiguration(
      ProviderConfiguration base,
      String webhookSigningSecret,
      CaptureMode captureMode,
      ConfirmationMethod confirmationMethod,
      String apiVersion,
      String statementDescriptor) {
    this.base = Validate.notNull(base, "base must not be null.");
    this.webhookSigningSecret =
        Validate.notBlank(webhookSigningSecret, "webhookSigningSecret must not be blank.");
    this.captureMode = Validate.notNull(captureMode, "captureMode must not be null.");
    this.confirmationMethod =
        Validate.notNull(confirmationMethod, "confirmationMethod must not be null.");
    this.apiVersion =
        apiVersion == null ? null : Validate.notBlank(apiVersion, "apiVersion must not be blank.");
    this.statementDescriptor =
        statementDescriptor == null
            ? null
            : Validate.notBlank(statementDescriptor, "statementDescriptor must not be blank.");
  }

  public static StripeConfiguration of(ProviderConfiguration base, String webhookSigningSecret) {
    return new StripeConfiguration(
        base, webhookSigningSecret, CaptureMode.MANUAL, ConfirmationMethod.MANUAL, null, null);
  }

  public static StripeConfiguration of(
      ProviderConfiguration base, String webhookSigningSecret, CaptureMode captureMode) {
    return new StripeConfiguration(
        base, webhookSigningSecret, captureMode, ConfirmationMethod.MANUAL, null, null);
  }

  public static StripeConfiguration of(
      ProviderConfiguration base,
      String webhookSigningSecret,
      CaptureMode captureMode,
      ConfirmationMethod confirmationMethod) {
    return new StripeConfiguration(
        base, webhookSigningSecret, captureMode, confirmationMethod, null, null);
  }

  public static StripeConfiguration of(
      ProviderConfiguration base,
      String webhookSigningSecret,
      CaptureMode captureMode,
      ConfirmationMethod confirmationMethod,
      String apiVersion) {
    return new StripeConfiguration(
        base, webhookSigningSecret, captureMode, confirmationMethod, apiVersion, null);
  }

  public static StripeConfiguration of(
      ProviderConfiguration base,
      String webhookSigningSecret,
      CaptureMode captureMode,
      ConfirmationMethod confirmationMethod,
      String apiVersion,
      String statementDescriptor) {
    return new StripeConfiguration(
        base,
        webhookSigningSecret,
        captureMode,
        confirmationMethod,
        apiVersion,
        statementDescriptor);
  }

  public ProviderConfiguration base() {
    return base;
  }

  public String webhookSigningSecret() {
    return webhookSigningSecret;
  }

  public CaptureMode captureMode() {
    return captureMode;
  }

  public ConfirmationMethod confirmationMethod() {
    return confirmationMethod;
  }

  /** The Stripe API version to pin requests to, or {@code null} to use the SDK's default. */
  public String apiVersion() {
    return apiVersion;
  }

  /**
   * The text (or dynamic-suffix template) shown on the customer's card statement, or {@code null}
   * to leave Stripe's account-level default in effect.
   */
  public String statementDescriptor() {
    return statementDescriptor;
  }

  public String secretKey() {
    return base.credential().attributes().get("apiKey");
  }
}
