package io.genfin.payment.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class PaymentId extends Identifier {

  private PaymentId(String value) {
    super(value);
  }

  public static PaymentId of(String value) {
    return new PaymentId(value);
  }

  public static PaymentId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static PaymentId generate(IdentifierGenerator generator) {
    return new PaymentId(generator.generate());
  }
}
