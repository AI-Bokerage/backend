package io.genfin.pricing.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class CouponId extends Identifier {

  private CouponId(String value) {
    super(value);
  }

  public static CouponId of(String value) {
    return new CouponId(value);
  }

  public static CouponId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static CouponId generate(IdentifierGenerator generator) {
    return new CouponId(generator.generate());
  }
}
