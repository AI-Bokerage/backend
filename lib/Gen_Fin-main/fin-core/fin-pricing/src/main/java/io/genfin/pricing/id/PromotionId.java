package io.genfin.pricing.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class PromotionId extends Identifier {

  private PromotionId(String value) {
    super(value);
  }

  public static PromotionId of(String value) {
    return new PromotionId(value);
  }

  public static PromotionId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static PromotionId generate(IdentifierGenerator generator) {
    return new PromotionId(generator.generate());
  }
}
