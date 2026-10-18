package io.genfin.pricing.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class PriceComponentId extends Identifier {

  private PriceComponentId(String value) {
    super(value);
  }

  public static PriceComponentId of(String value) {
    return new PriceComponentId(value);
  }

  public static PriceComponentId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static PriceComponentId generate(IdentifierGenerator generator) {
    return new PriceComponentId(generator.generate());
  }
}
