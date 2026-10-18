package io.genfin.document.api.identity;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

/** Identifies a page layout applied when composing a document. */
public final class LayoutId extends Identifier {

  private LayoutId(String value) {
    super(value);
  }

  public static LayoutId of(String value) {
    return new LayoutId(value);
  }

  public static LayoutId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static LayoutId generate(IdentifierGenerator generator) {
    return new LayoutId(generator.generate());
  }
}
