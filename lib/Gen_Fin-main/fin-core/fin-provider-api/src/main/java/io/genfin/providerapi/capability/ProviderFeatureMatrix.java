package io.genfin.providerapi.capability;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.payment.method.PaymentMethodType;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A provider's declared support matrix — which {@link CaptureMode}s it can honor for which payment
 * method types, and which currencies it will accept — so a gateway can reject an unsupported
 * request before making a network call instead of letting the provider SDK's own error surface raw.
 * Mirrors the {@link CapabilitySet}/{@link ProviderCapability} pattern rather than inventing a
 * parallel one.
 */
public record ProviderFeatureMatrix(
    Set<CaptureMode> supportedCaptureModes,
    Set<String> supportedPaymentMethodCodes,
    Set<String> supportedCurrencyCodes)
    implements ValueObject {

  public ProviderFeatureMatrix {
    Validate.notNull(supportedCaptureModes, "supportedCaptureModes must not be null.");
    Validate.notNull(supportedPaymentMethodCodes, "supportedPaymentMethodCodes must not be null.");
    Validate.notNull(supportedCurrencyCodes, "supportedCurrencyCodes must not be null.");
    supportedCaptureModes = Set.copyOf(supportedCaptureModes);
    supportedPaymentMethodCodes = normalize(supportedPaymentMethodCodes);
    supportedCurrencyCodes = normalize(supportedCurrencyCodes);
  }

  private static Set<String> normalize(Set<String> values) {
    return values.stream()
        .map(value -> value.toUpperCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** Whether {@code captureMode} is usable together with {@code method} on this provider. */
  public boolean supportsCaptureMode(CaptureMode captureMode, PaymentMethodType method) {
    return supportedCaptureModes.contains(captureMode)
        && supportedPaymentMethodCodes.contains(method.code().toUpperCase(Locale.ROOT));
  }

  /** Whether {@code currencyCode} (an ISO-4217 alpha code) is accepted by this provider. */
  public boolean supportsCurrency(String currencyCode) {
    return supportedCurrencyCodes.contains(currencyCode.toUpperCase(Locale.ROOT));
  }
}
