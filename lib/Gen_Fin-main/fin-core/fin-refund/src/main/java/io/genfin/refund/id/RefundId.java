package io.genfin.refund.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class RefundId extends Identifier {

  private RefundId(String value) {
    super(value);
  }

  public static RefundId of(String value) {
    return new RefundId(value);
  }

  public static RefundId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static RefundId generate(IdentifierGenerator generator) {
    return new RefundId(generator.generate());
  }
}
