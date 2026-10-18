package io.genfin.document.api.identity;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

/** Identifies a file attached to a document. */
public final class AttachmentId extends Identifier {

  private AttachmentId(String value) {
    super(value);
  }

  public static AttachmentId of(String value) {
    return new AttachmentId(value);
  }

  public static AttachmentId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static AttachmentId generate(IdentifierGenerator generator) {
    return new AttachmentId(generator.generate());
  }
}
