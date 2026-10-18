package io.genfin.api.id;

import io.genfin.api.port.id.IdentifierGenerator;

public final class TransactionId extends Identifier {

  private TransactionId(String value) {
    super(value);
  }

  public static TransactionId of(String value) {
    return new TransactionId(value);
  }

  public static TransactionId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static TransactionId generate(IdentifierGenerator generator) {
    return new TransactionId(generator.generate());
  }
}
