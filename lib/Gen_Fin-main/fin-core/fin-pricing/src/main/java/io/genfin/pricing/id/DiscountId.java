package io.genfin.pricing.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class DiscountId extends Identifier {

  private DiscountId(String value) {
    super(value);
  }

  public static DiscountId of(String value) {
    return new DiscountId(value);
  }

  public static DiscountId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static DiscountId generate(IdentifierGenerator generator) {
    return new DiscountId(generator.generate());
  }
}
