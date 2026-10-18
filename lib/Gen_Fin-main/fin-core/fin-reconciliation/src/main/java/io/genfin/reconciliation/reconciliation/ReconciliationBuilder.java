package io.genfin.reconciliation.reconciliation;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.reconciliation.id.ReconciliationBatchId;
import io.genfin.reconciliation.id.ReconciliationId;
import io.genfin.reconciliation.lifecycle.ReconciliationEvent;
import io.genfin.reconciliation.lifecycle.ReconciliationLifecycles;
import io.genfin.reconciliation.lifecycle.ReconciliationStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link Reconciliation} aggregates. Preferred over the canonical constructor for
 * readability at call sites, and lets callers seed initial items/metadata in one expression instead
 * of a constructor call followed by a run of {@code addItem}/{@code putMetadata} calls.
 */
public final class ReconciliationBuilder {

  private ReconciliationId id;
  private ReconciliationBatchId batchId;
  private StateMachine<ReconciliationStatus, ReconciliationEvent> lifecycle;
  private final List<ReconciliationItem> items = new ArrayList<>();
  private final Map<String, String> metadata = new LinkedHashMap<>();

  private ReconciliationBuilder() {}

  public static ReconciliationBuilder newReconciliation() {
    return new ReconciliationBuilder();
  }

  public ReconciliationBuilder id(ReconciliationId id) {
    this.id = id;
    return this;
  }

  public ReconciliationBuilder batchId(ReconciliationBatchId batchId) {
    this.batchId = batchId;
    return this;
  }

  public ReconciliationBuilder lifecycle(
      StateMachine<ReconciliationStatus, ReconciliationEvent> lifecycle) {
    this.lifecycle = lifecycle;
    return this;
  }

  public ReconciliationBuilder item(ReconciliationItem item) {
    items.add(item);
    return this;
  }

  public ReconciliationBuilder items(List<ReconciliationItem> items) {
    this.items.addAll(items);
    return this;
  }

  public ReconciliationBuilder metadata(String key, String value) {
    metadata.put(key, value);
    return this;
  }

  public Reconciliation build() {
    Reconciliation reconciliation =
        new Reconciliation(
            id == null ? ReconciliationId.generate() : id,
            batchId,
            lifecycle != null ? lifecycle : ReconciliationLifecycles.created());
    items.forEach(reconciliation::addItem);
    metadata.forEach(reconciliation::putMetadata);
    return reconciliation;
  }
}
