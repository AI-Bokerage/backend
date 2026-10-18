package io.genfin.reconciliation.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class DiscrepancyId extends Identifier {

  private DiscrepancyId(String value) {
    super(value);
  }

  public static DiscrepancyId of(String value) {
    return new DiscrepancyId(value);
  }

  public static DiscrepancyId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static DiscrepancyId generate(IdentifierGenerator generator) {
    return new DiscrepancyId(generator.generate());
  }
}
