package io.genfin.dunning.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class ObligationId extends Identifier {

  private ObligationId(String value) {
    super(value);
  }

  public static ObligationId of(String value) {
    return new ObligationId(value);
  }

  public static ObligationId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static ObligationId generate(IdentifierGenerator generator) {
    return new ObligationId(generator.generate());
  }
}
