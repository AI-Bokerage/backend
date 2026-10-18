package io.genfin.document.api.identity;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

/** Identifies the brand a document is issued under. */
public final class BrandId extends Identifier {

  private BrandId(String value) {
    super(value);
  }

  public static BrandId of(String value) {
    return new BrandId(value);
  }

  public static BrandId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static BrandId generate(IdentifierGenerator generator) {
    return new BrandId(generator.generate());
  }
}
