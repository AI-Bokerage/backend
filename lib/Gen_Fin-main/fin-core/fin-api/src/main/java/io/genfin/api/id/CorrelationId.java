package io.genfin.api.id;

import io.genfin.api.port.id.IdentifierGenerator;

public final class CorrelationId extends Identifier {

  private CorrelationId(String value) {
    super(value);
  }

  public static CorrelationId of(String value) {
    return new CorrelationId(value);
  }

  public static CorrelationId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static CorrelationId generate(IdentifierGenerator generator) {
    return new CorrelationId(generator.generate());
  }
}
