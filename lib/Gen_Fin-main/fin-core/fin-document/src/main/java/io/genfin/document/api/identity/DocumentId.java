package io.genfin.document.api.identity;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

/** Identifies a single rendered or renderable document. */
public final class DocumentId extends Identifier {

  private DocumentId(String value) {
    super(value);
  }

  public static DocumentId of(String value) {
    return new DocumentId(value);
  }

  public static DocumentId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static DocumentId generate(IdentifierGenerator generator) {
    return new DocumentId(generator.generate());
  }
}
