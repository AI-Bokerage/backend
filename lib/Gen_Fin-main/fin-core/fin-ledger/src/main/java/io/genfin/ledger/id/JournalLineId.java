package io.genfin.ledger.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class JournalLineId extends Identifier {

  private JournalLineId(String value) {
    super(value);
  }

  public static JournalLineId of(String value) {
    return new JournalLineId(value);
  }

  public static JournalLineId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static JournalLineId generate(IdentifierGenerator generator) {
    return new JournalLineId(generator.generate());
  }
}
