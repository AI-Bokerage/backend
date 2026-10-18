package io.genfin.payment.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class AttemptId extends Identifier {

  private AttemptId(String value) {
    super(value);
  }

  public static AttemptId of(String value) {
    return new AttemptId(value);
  }

  public static AttemptId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static AttemptId generate(IdentifierGenerator generator) {
    return new AttemptId(generator.generate());
  }
}
