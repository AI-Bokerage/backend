package io.genfin.reconciliation.discrepancy;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyDetector;

/**
 * Resolves the {@link DiscrepancyDetector} registered in an {@link ExtensionRegistry}, falling back
 * to {@link DiscrepancyDetectors#standard()}.
 */
public final class DiscrepancyDetectorFactory {

  private DiscrepancyDetectorFactory() {}

  public static DiscrepancyDetector from(ExtensionRegistry registry) {
    return registry.find(DiscrepancyDetector.class).orElseGet(DiscrepancyDetectors::standard);
  }
}
