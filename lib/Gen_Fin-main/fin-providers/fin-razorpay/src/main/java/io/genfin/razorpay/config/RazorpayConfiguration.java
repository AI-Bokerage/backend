package io.genfin.razorpay.config;

import io.genfin.api.validation.Validate;
import io.genfin.providerapi.capability.CaptureMode;
import io.genfin.providerapi.config.ProviderConfiguration;

/** Razorpay-specific settings layered on top of the generic {@link ProviderConfiguration}. */
public final class RazorpayConfiguration {

  private final ProviderConfiguration base;
  private final String keySecret;
  private final String webhookSecret;
  private final CaptureMode captureMode;

  private RazorpayConfiguration(
      ProviderConfiguration base, String keySecret, String webhookSecret, CaptureMode captureMode) {
    this.base = Validate.notNull(base, "base must not be null.");
    this.keySecret = Validate.notBlank(keySecret, "keySecret must not be blank.");
    this.webhookSecret = Validate.notBlank(webhookSecret, "webhookSecret must not be blank.");
    this.captureMode = Validate.notNull(captureMode, "captureMode must not be null.");
  }

  public static RazorpayConfiguration of(
      ProviderConfiguration base, String keySecret, String webhookSecret) {
    return new RazorpayConfiguration(base, keySecret, webhookSecret, CaptureMode.MANUAL);
  }

  public static RazorpayConfiguration of(
      ProviderConfiguration base, String keySecret, String webhookSecret, CaptureMode captureMode) {
    return new RazorpayConfiguration(base, keySecret, webhookSecret, captureMode);
  }

  public ProviderConfiguration base() {
    return base;
  }

  public CaptureMode captureMode() {
    return captureMode;
  }

  public String keyId() {
    return base.credential().attributes().get("apiKey");
  }

  public String keySecret() {
    return keySecret;
  }

  public String webhookSecret() {
    return webhookSecret;
  }
}
