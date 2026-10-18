package io.genfin.refund.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class RefundRequestId extends Identifier {

  private RefundRequestId(String value) {
    super(value);
  }

  public static RefundRequestId of(String value) {
    return new RefundRequestId(value);
  }

  public static RefundRequestId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static RefundRequestId generate(IdentifierGenerator generator) {
    return new RefundRequestId(generator.generate());
  }
}
