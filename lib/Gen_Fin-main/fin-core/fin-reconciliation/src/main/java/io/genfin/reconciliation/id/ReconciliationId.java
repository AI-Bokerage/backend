package io.genfin.reconciliation.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class ReconciliationId extends Identifier {

  private ReconciliationId(String value) {
    super(value);
  }

  public static ReconciliationId of(String value) {
    return new ReconciliationId(value);
  }

  public static ReconciliationId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static ReconciliationId generate(IdentifierGenerator generator) {
    return new ReconciliationId(generator.generate());
  }
}
