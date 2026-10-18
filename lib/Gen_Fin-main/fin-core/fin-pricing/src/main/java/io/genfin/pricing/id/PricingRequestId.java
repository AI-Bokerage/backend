package io.genfin.pricing.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class PricingRequestId extends Identifier {

  private PricingRequestId(String value) {
    super(value);
  }

  public static PricingRequestId of(String value) {
    return new PricingRequestId(value);
  }

  public static PricingRequestId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static PricingRequestId generate(IdentifierGenerator generator) {
    return new PricingRequestId(generator.generate());
  }
}
