package io.genfin.invoice.serialization;

import io.genfin.api.exception.SerializationException;
import io.genfin.api.serialization.Codec;
import io.genfin.api.serialization.SerializationFormat;
import io.genfin.invoice.reference.Reference;
import io.genfin.invoice.reference.StandardReferenceType;
import java.nio.charset.StandardCharsets;

/**
 * A compact {@code typeCode<US>value<US>label} codec for {@link Reference}, using the unit
 * separator (U+001F) as delimiter.
 */
public final class ReferenceCodec implements Codec<Reference> {

  private static final String DELIMITER = "";
  private static final int FIELD_COUNT = 3;

  @Override
  public byte[] serialize(Reference value) {
    String text =
        String.join(
            DELIMITER,
            value.type().code(),
            value.value().value(),
            value.displayLabel() == null ? "" : value.displayLabel());
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Reference deserialize(byte[] data) {
    String[] parts = new String(data, StandardCharsets.UTF_8).split(DELIMITER, -1);
    if (parts.length != FIELD_COUNT) {
      throw new SerializationException("Malformed Reference payload", null);
    }
    String label = parts[2].isEmpty() ? null : parts[2];
    return Reference.of(StandardReferenceType.of(parts[0]), parts[1], label);
  }

  @Override
  public SerializationFormat format() {
    return SerializationFormat.BINARY;
  }
}
