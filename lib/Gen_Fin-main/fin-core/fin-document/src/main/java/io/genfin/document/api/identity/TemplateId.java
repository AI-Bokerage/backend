package io.genfin.document.api.identity;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

/** Identifies a document template used during composition. */
public final class TemplateId extends Identifier {

  private TemplateId(String value) {
    super(value);
  }

  public static TemplateId of(String value) {
    return new TemplateId(value);
  }

  public static TemplateId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static TemplateId generate(IdentifierGenerator generator) {
    return new TemplateId(generator.generate());
  }
}
