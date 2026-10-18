package io.genfin.ledger.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class PostingId extends Identifier {

  private PostingId(String value) {
    super(value);
  }

  public static PostingId of(String value) {
    return new PostingId(value);
  }

  public static PostingId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static PostingId generate(IdentifierGenerator generator) {
    return new PostingId(generator.generate());
  }
}
