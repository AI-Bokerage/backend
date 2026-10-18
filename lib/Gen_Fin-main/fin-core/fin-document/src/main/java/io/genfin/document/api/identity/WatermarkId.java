package io.genfin.document.api.identity;

import io.genfin.api.id.Identifier;
import io.genfin.api.id.IdentifierGenerators;
import io.genfin.api.port.id.IdentifierGenerator;

/** Identifies a watermark asset applied to a document. */
public final class WatermarkId extends Identifier {

  private WatermarkId(String value) {
    super(value);
  }

  public static WatermarkId of(String value) {
    return new WatermarkId(value);
  }

  public static WatermarkId generate() {
    return generate(IdentifierGenerators.uuidV4());
  }

  public static WatermarkId generate(IdentifierGenerator generator) {
    return new WatermarkId(generator.generate());
  }
}
