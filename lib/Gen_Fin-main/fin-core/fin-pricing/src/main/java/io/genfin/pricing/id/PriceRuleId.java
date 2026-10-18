package io.genfin.pricing.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class PriceRuleId extends Identifier {

  private PriceRuleId(String value) {
    super(value);
  }

  public static PriceRuleId of(String value) {
    return new PriceRuleId(value);
  }

  public static PriceRuleId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static PriceRuleId generate(IdentifierGenerator generator) {
    return new PriceRuleId(generator.generate());
  }
}
