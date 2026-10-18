package io.genfin.pricing.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class PricingResultId extends Identifier {

  private PricingResultId(String value) {
    super(value);
  }

  public static PricingResultId of(String value) {
    return new PricingResultId(value);
  }

  public static PricingResultId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static PricingResultId generate(IdentifierGenerator generator) {
    return new PricingResultId(generator.generate());
  }
}
