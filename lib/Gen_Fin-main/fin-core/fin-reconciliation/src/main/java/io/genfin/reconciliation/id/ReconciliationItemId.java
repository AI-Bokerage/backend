package io.genfin.reconciliation.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class ReconciliationItemId extends Identifier {

  private ReconciliationItemId(String value) {
    super(value);
  }

  public static ReconciliationItemId of(String value) {
    return new ReconciliationItemId(value);
  }

  public static ReconciliationItemId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static ReconciliationItemId generate(IdentifierGenerator generator) {
    return new ReconciliationItemId(generator.generate());
  }
}
