package io.genfin.api.id;

import io.genfin.api.port.id.IdentifierGenerator;

public final class ReferenceId extends Identifier {

  private ReferenceId(String value) {
    super(value);
  }

  public static ReferenceId of(String value) {
    return new ReferenceId(value);
  }

  public static ReferenceId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static ReferenceId generate(IdentifierGenerator generator) {
    return new ReferenceId(generator.generate());
  }
}
