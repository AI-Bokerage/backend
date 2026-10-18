package io.genfin.payment.gateway;

import io.genfin.api.domain.ValueObject;
import java.util.Set;

/**
 * Business-level facts about the organization behind a gateway — as opposed to {@link
 * GatewayCapabilities}, which is API-operation-level.
 */
public record ProviderCapabilities(
    Set<String> supportedCurrencyCodes,
    Set<String> supportedCountryCodes,
    boolean pciCompliant,
    boolean supportsThreeDSecure)
    implements ValueObject {

  public ProviderCapabilities {
    supportedCurrencyCodes = Set.copyOf(supportedCurrencyCodes);
    supportedCountryCodes = Set.copyOf(supportedCountryCodes);
  }
}
