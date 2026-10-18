package io.genfin.invoice.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class LineId extends Identifier {

  private LineId(String value) {
    super(value);
  }

  public static LineId of(String value) {
    return new LineId(value);
  }

  public static LineId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static LineId generate(IdentifierGenerator generator) {
    return new LineId(generator.generate());
  }
}
