package io.genfin.document.api.identity;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

/** Identifies a letterhead asset applied to a document. */
public final class LetterheadId extends Identifier {

  private LetterheadId(String value) {
    super(value);
  }

  public static LetterheadId of(String value) {
    return new LetterheadId(value);
  }

  public static LetterheadId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static LetterheadId generate(IdentifierGenerator generator) {
    return new LetterheadId(generator.generate());
  }
}
