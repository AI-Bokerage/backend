package io.genfin.pricing.pricing;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.lifecycle.PricingLifecycles;
import io.genfin.pricing.lifecycle.PricingRequestStatus;
import io.genfin.pricing.port.lifecycle.PricingLifecycleProvider;
import java.time.Instant;
import java.util.List;

/**
 * Creates {@link PricingRequest}s wired to the {@link PricingLifecycleProvider} registered in an
 * {@link ExtensionRegistry}, falling back to {@link PricingLifecycles#standard()}. Preferred over
 * {@link PricingRequestBuilder} directly whenever the lifecycle should come from the deployment's
 * configured extensions rather than the built-in default. Mirrors {@code
 * io.genfin.ledger.journal.JournalFactory}.
 */
public final class PricingRequestFactory {

  private PricingRequestFactory() {}

  public static PricingRequest create(
      ExtensionRegistry registry, List<PricingRequest.Line> lines, Instant requestedAt) {
    PricingLifecycleProvider lifecycleProvider =
        registry.find(PricingLifecycleProvider.class).orElseGet(PricingLifecycles::standard);
    return PricingRequestBuilder.newRequest()
        .lines(lines)
        .requestedAt(requestedAt)
        .lifecycle(lifecycleProvider.create(PricingRequestStatus.CREATED))
        .build();
  }
}
