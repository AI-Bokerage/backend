package io.genfin.pricing.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class CreditId extends Identifier {

  private CreditId(String value) {
    super(value);
  }

  public static CreditId of(String value) {
    return new CreditId(value);
  }

  public static CreditId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static CreditId generate(IdentifierGenerator generator) {
    return new CreditId(generator.generate());
  }
}
