package io.genfin.reconciliation.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class ComparisonId extends Identifier {

  private ComparisonId(String value) {
    super(value);
  }

  public static ComparisonId of(String value) {
    return new ComparisonId(value);
  }

  public static ComparisonId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static ComparisonId generate(IdentifierGenerator generator) {
    return new ComparisonId(generator.generate());
  }
}
