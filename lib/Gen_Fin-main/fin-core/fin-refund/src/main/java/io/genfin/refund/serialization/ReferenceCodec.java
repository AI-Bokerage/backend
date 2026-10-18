package io.genfin.refund.serialization;

import io.genfin.api.exception.SerializationException;
import io.genfin.api.serialization.Codec;
import io.genfin.api.serialization.SerializationFormat;
import io.genfin.refund.reference.Reference;
import io.genfin.refund.reference.StandardReferenceType;
import java.nio.charset.StandardCharsets;

/**
 * A compact {@code typeCode<US>value} codec for {@link Reference}, using the unit separator
 * (U+001F) as delimiter. Mirrors {@code io.genfin.payment.serialization.ReferenceCodec} for
 * fin-refund's own {@code Reference} type.
 */
public final class ReferenceCodec implements Codec<Reference> {

  private static final String DELIMITER = "";
  private static final int FIELD_COUNT = 2;

  @Override
  public byte[] serialize(Reference value) {
    String text = String.join(DELIMITER, value.type().code(), value.value().value());
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Reference deserialize(byte[] data) {
    String[] parts = new String(data, StandardCharsets.UTF_8).split(DELIMITER, -1);
    if (parts.length != FIELD_COUNT) {
      throw new SerializationException("Malformed Reference payload", null);
    }
    return Reference.of(StandardReferenceType.of(parts[0]), parts[1]);
  }

  @Override
  public SerializationFormat format() {
    return SerializationFormat.BINARY;
  }
}
