package io.genfin.reconciliation.reconciliation;

import io.genfin.money.money.Money;
import io.genfin.reconciliation.id.ReconciliationItemId;
import io.genfin.refund.reference.Reference;

/**
 * Builds {@link ReconciliationItem}s. Preferred over the canonical constructor for readability at
 * call sites that also need to override the generated {@link ReconciliationItemId}.
 */
public final class ReconciliationItemBuilder {

  private ReconciliationItemId id;
  private Reference source;
  private Money amount;

  private ReconciliationItemBuilder() {}

  public static ReconciliationItemBuilder newItem() {
    return new ReconciliationItemBuilder();
  }

  public ReconciliationItemBuilder id(ReconciliationItemId id) {
    this.id = id;
    return this;
  }

  public ReconciliationItemBuilder source(Reference source) {
    this.source = source;
    return this;
  }

  public ReconciliationItemBuilder amount(Money amount) {
    this.amount = amount;
    return this;
  }

  public ReconciliationItem build() {
    return new ReconciliationItem(
        id == null ? ReconciliationItemId.generate() : id, source, amount);
  }
}
