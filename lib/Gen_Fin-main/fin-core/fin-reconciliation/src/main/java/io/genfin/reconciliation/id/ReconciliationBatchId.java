package io.genfin.reconciliation.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class ReconciliationBatchId extends Identifier {

  private ReconciliationBatchId(String value) {
    super(value);
  }

  public static ReconciliationBatchId of(String value) {
    return new ReconciliationBatchId(value);
  }

  public static ReconciliationBatchId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static ReconciliationBatchId generate(IdentifierGenerator generator) {
    return new ReconciliationBatchId(generator.generate());
  }
}
