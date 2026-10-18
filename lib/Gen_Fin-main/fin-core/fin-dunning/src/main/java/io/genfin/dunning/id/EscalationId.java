package io.genfin.dunning.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class EscalationId extends Identifier {

  private EscalationId(String value) {
    super(value);
  }

  public static EscalationId of(String value) {
    return new EscalationId(value);
  }

  public static EscalationId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static EscalationId generate(IdentifierGenerator generator) {
    return new EscalationId(generator.generate());
  }
}
