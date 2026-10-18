package io.genfin.ledger.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class JournalEntryId extends Identifier {

  private JournalEntryId(String value) {
    super(value);
  }

  public static JournalEntryId of(String value) {
    return new JournalEntryId(value);
  }

  public static JournalEntryId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static JournalEntryId generate(IdentifierGenerator generator) {
    return new JournalEntryId(generator.generate());
  }
}
