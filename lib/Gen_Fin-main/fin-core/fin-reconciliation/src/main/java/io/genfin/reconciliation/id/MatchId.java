package io.genfin.reconciliation.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class MatchId extends Identifier {

  private MatchId(String value) {
    super(value);
  }

  public static MatchId of(String value) {
    return new MatchId(value);
  }

  public static MatchId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static MatchId generate(IdentifierGenerator generator) {
    return new MatchId(generator.generate());
  }
}
