package io.genfin.refund.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class RefundAttemptId extends Identifier {

  private RefundAttemptId(String value) {
    super(value);
  }

  public static RefundAttemptId of(String value) {
    return new RefundAttemptId(value);
  }

  public static RefundAttemptId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static RefundAttemptId generate(IdentifierGenerator generator) {
    return new RefundAttemptId(generator.generate());
  }
}
