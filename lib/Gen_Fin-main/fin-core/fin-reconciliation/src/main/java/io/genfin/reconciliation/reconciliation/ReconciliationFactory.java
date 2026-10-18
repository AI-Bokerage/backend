package io.genfin.reconciliation.reconciliation;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.reconciliation.id.ReconciliationBatchId;
import io.genfin.reconciliation.lifecycle.ReconciliationLifecycles;
import io.genfin.reconciliation.lifecycle.StandardReconciliationStatus;
import io.genfin.reconciliation.port.lifecycle.ReconciliationLifecycleProvider;

/**
 * Creates {@link Reconciliation}s wired to the {@link ReconciliationLifecycleProvider} registered
 * in an {@link ExtensionRegistry}, falling back to {@link ReconciliationLifecycles#standard()}.
 * Preferred over {@link ReconciliationBuilder} directly whenever the lifecycle should come from the
 * deployment's configured extensions rather than the built-in default.
 */
public final class ReconciliationFactory {

  private ReconciliationFactory() {}

  public static Reconciliation create(ExtensionRegistry registry, ReconciliationBatchId batchId) {
    ReconciliationLifecycleProvider lifecycleProvider =
        registry
            .find(ReconciliationLifecycleProvider.class)
            .orElseGet(ReconciliationLifecycles::standard);
    return ReconciliationBuilder.newReconciliation()
        .batchId(batchId)
        .lifecycle(lifecycleProvider.create(StandardReconciliationStatus.CREATED))
        .build();
  }
}
