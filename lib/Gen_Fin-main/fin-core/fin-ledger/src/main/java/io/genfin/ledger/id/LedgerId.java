package io.genfin.ledger.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class LedgerId extends Identifier {

  private LedgerId(String value) {
    super(value);
  }

  public static LedgerId of(String value) {
    return new LedgerId(value);
  }

  public static LedgerId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static LedgerId generate(IdentifierGenerator generator) {
    return new LedgerId(generator.generate());
  }
}
