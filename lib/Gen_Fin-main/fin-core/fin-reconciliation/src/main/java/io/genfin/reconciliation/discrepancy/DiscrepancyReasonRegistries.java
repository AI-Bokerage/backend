package io.genfin.reconciliation.discrepancy;

import io.genfin.reconciliation.internal.discrepancy.DefaultDiscrepancyReasonRegistry;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyReasonProvider;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyReasonRegistry;
import java.util.List;

public final class DiscrepancyReasonRegistries {

  private DiscrepancyReasonRegistries() {}

  public static DiscrepancyReasonRegistry empty() {
    return new DefaultDiscrepancyReasonRegistry();
  }

  public static DiscrepancyReasonRegistry withProvider(DiscrepancyReasonProvider provider) {
    DiscrepancyReasonRegistry registry = new DefaultDiscrepancyReasonRegistry();
    provider.provide().forEach(registry::register);
    return registry;
  }

  public static DiscrepancyReasonProvider standardCatalog() {
    return () ->
        List.of(
            new DiscrepancyReasonDescriptor(
                StandardDiscrepancyReason.AMOUNT_OUT_OF_TOLERANCE, "Amount Out Of Tolerance"),
            new DiscrepancyReasonDescriptor(
                StandardDiscrepancyReason.CURRENCY_MISMATCH, "Currency Mismatch"),
            new DiscrepancyReasonDescriptor(
                StandardDiscrepancyReason.REFERENCE_MISMATCH, "Reference Mismatch"),
            new DiscrepancyReasonDescriptor(
                StandardDiscrepancyReason.STATUS_MISMATCH, "Status Mismatch"),
            new DiscrepancyReasonDescriptor(
                StandardDiscrepancyReason.TIMESTAMP_DRIFT, "Timestamp Drift"),
            new DiscrepancyReasonDescriptor(
                StandardDiscrepancyReason.METADATA_MISMATCH, "Metadata Mismatch"),
            new DiscrepancyReasonDescriptor(
                StandardDiscrepancyReason.ATTRIBUTE_MISMATCH, "Attribute Mismatch"),
            new DiscrepancyReasonDescriptor(
                StandardDiscrepancyReason.MISSING_COUNTERPART, "Missing Counterpart"),
            new DiscrepancyReasonDescriptor(StandardDiscrepancyReason.UNKNOWN, "Unknown"));
  }
}
