package io.genfin.document.api.identity;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

/** Identifies a registered DocumentRenderer, e.g. "json" or "markdown". */
public final class RendererId extends Identifier {

  private RendererId(String value) {
    super(value);
  }

  public static RendererId of(String value) {
    return new RendererId(value);
  }

  public static RendererId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static RendererId generate(IdentifierGenerator generator) {
    return new RendererId(generator.generate());
  }
}
