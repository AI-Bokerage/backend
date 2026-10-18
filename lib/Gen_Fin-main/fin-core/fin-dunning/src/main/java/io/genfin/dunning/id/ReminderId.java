package io.genfin.dunning.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class ReminderId extends Identifier {

  private ReminderId(String value) {
    super(value);
  }

  public static ReminderId of(String value) {
    return new ReminderId(value);
  }

  public static ReminderId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static ReminderId generate(IdentifierGenerator generator) {
    return new ReminderId(generator.generate());
  }
}
