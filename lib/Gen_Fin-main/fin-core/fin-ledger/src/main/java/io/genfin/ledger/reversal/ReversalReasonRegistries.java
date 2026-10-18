package io.genfin.ledger.reversal;

import io.genfin.ledger.internal.reversal.DefaultReversalReasonRegistry;
import io.genfin.ledger.port.reversal.ReversalReasonProvider;
import io.genfin.ledger.port.reversal.ReversalReasonRegistry;
import java.util.List;

/** Factory for {@link ReversalReasonRegistry} instances. Mirrors {@code RefundReasonRegistries}. */
public final class ReversalReasonRegistries {

  private ReversalReasonRegistries() {}

  public static ReversalReasonRegistry empty() {
    return new DefaultReversalReasonRegistry();
  }

  public static ReversalReasonRegistry withProvider(ReversalReasonProvider provider) {
    ReversalReasonRegistry registry = new DefaultReversalReasonRegistry();
    provider.provide().forEach(registry::register);
    return registry;
  }

  public static ReversalReasonProvider standardCatalog() {
    return () ->
        List.of(
            new ReversalReasonDescriptor(
                StandardReversalReason.DATA_ENTRY_ERROR, "Data Entry Error"),
            new ReversalReasonDescriptor(
                StandardReversalReason.DUPLICATE_POSTING, "Duplicate Posting"),
            new ReversalReasonDescriptor(
                StandardReversalReason.INCORRECT_ACCOUNT, "Incorrect Account"),
            new ReversalReasonDescriptor(
                StandardReversalReason.INCORRECT_AMOUNT, "Incorrect Amount"),
            new ReversalReasonDescriptor(StandardReversalReason.WRONG_PERIOD, "Wrong Period"),
            new ReversalReasonDescriptor(StandardReversalReason.SYSTEM_ERROR, "System Error"),
            new ReversalReasonDescriptor(
                StandardReversalReason.AUDIT_ADJUSTMENT, "Audit Adjustment"),
            new ReversalReasonDescriptor(
                StandardReversalReason.MANUAL_CORRECTION, "Manual Correction"));
  }
}
