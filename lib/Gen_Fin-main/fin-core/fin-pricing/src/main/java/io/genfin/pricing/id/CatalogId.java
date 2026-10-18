package io.genfin.pricing.id;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

public final class CatalogId extends Identifier {

  private CatalogId(String value) {
    super(value);
  }

  public static CatalogId of(String value) {
    return new CatalogId(value);
  }

  public static CatalogId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static CatalogId generate(IdentifierGenerator generator) {
    return new CatalogId(generator.generate());
  }
}
