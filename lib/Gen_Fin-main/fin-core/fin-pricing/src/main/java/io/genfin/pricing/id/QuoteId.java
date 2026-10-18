package io.genfin.pricing.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class QuoteId extends Identifier {

  private QuoteId(String value) {
    super(value);
  }

  public static QuoteId of(String value) {
    return new QuoteId(value);
  }

  public static QuoteId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static QuoteId generate(IdentifierGenerator generator) {
    return new QuoteId(generator.generate());
  }
}
