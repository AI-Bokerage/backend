package io.genfin.dunning.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class RetryId extends Identifier {

  private RetryId(String value) {
    super(value);
  }

  public static RetryId of(String value) {
    return new RetryId(value);
  }

  public static RetryId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static RetryId generate(IdentifierGenerator generator) {
    return new RetryId(generator.generate());
  }
}
