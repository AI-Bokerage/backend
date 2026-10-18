package io.genfin.payment.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class SessionId extends Identifier {

  private SessionId(String value) {
    super(value);
  }

  public static SessionId of(String value) {
    return new SessionId(value);
  }

  public static SessionId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static SessionId generate(IdentifierGenerator generator) {
    return new SessionId(generator.generate());
  }
}
