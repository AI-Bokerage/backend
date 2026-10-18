package io.genfin.ledger.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class PostingBatchId extends Identifier {

  private PostingBatchId(String value) {
    super(value);
  }

  public static PostingBatchId of(String value) {
    return new PostingBatchId(value);
  }

  public static PostingBatchId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static PostingBatchId generate(IdentifierGenerator generator) {
    return new PostingBatchId(generator.generate());
  }
}
