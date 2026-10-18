package io.genfin.document.api.identity;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

/** Identifies a visual theme applied to a document. */
public final class ThemeId extends Identifier {

  private ThemeId(String value) {
    super(value);
  }

  public static ThemeId of(String value) {
    return new ThemeId(value);
  }

  public static ThemeId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static ThemeId generate(IdentifierGenerator generator) {
    return new ThemeId(generator.generate());
  }
}
