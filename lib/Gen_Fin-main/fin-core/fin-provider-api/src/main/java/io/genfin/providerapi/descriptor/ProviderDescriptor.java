package io.genfin.providerapi.descriptor;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.payment.metadata.PaymentMetadata;

/**
 * Describes a provider itself — not a gateway instance. Reuses {@link PaymentMetadata} for
 * arbitrary provider metadata rather than inventing a duplicate bag type, since this module already
 * depends on {@code fin-payment}.
 */
public record ProviderDescriptor(
    ProviderId id,
    ProviderName name,
    ProviderVersion version,
    ProviderRegion region,
    ProviderEnvironment environment,
    ProviderPriority priority,
    ProviderStatus status,
    PaymentMetadata metadata)
    implements ValueObject {

  public ProviderDescriptor {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(name, "name must not be null.");
    Validate.notNull(version, "version must not be null.");
    Validate.notNull(region, "region must not be null.");
    Validate.notNull(environment, "environment must not be null.");
    Validate.notNull(priority, "priority must not be null.");
    Validate.notNull(status, "status must not be null.");
    if (metadata == null) {
      metadata = PaymentMetadata.empty();
    }
  }

  public boolean isUsable() {
    return status == ProviderStatus.ACTIVE || status == ProviderStatus.DEGRADED;
  }
}
