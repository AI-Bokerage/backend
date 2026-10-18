package io.genfin.api.id;

import io.genfin.api.port.id.IdentifierGenerator;

public final class RequestId extends Identifier {

  private RequestId(String value) {
    super(value);
  }

  public static RequestId of(String value) {
    return new RequestId(value);
  }

  public static RequestId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static RequestId generate(IdentifierGenerator generator) {
    return new RequestId(generator.generate());
  }
}
