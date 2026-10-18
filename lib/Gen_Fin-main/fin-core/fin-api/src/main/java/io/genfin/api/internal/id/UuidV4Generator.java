package io.genfin.api.internal.id;

import io.genfin.api.port.id.IdentifierGenerator;
import java.util.UUID;

public final class UuidV4Generator implements IdentifierGenerator {

  @Override
  public String generate() {
    return UUID.randomUUID().toString();
  }
}
