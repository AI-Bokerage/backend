package io.genfin.reconciliation.serialization;

import io.genfin.api.exception.SerializationException;
import io.genfin.api.serialization.Codec;
import io.genfin.api.serialization.SerializationFormat;
import io.genfin.reconciliation.report.ReportEntry;
import java.nio.charset.StandardCharsets;

/**
 * A compact {@code label<US>value} codec for {@link ReportEntry}, using the unit separator (U+001F)
 * as delimiter. Mirrors {@code io.genfin.refund.serialization.ReferenceCodec} for
 * fin-reconciliation's own reporting value object.
 */
public final class ReportEntryCodec implements Codec<ReportEntry> {

  private static final String DELIMITER = "";
  private static final int FIELD_COUNT = 2;

  @Override
  public byte[] serialize(ReportEntry value) {
    String text = String.join(DELIMITER, value.label(), value.value());
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public ReportEntry deserialize(byte[] data) {
    String[] parts = new String(data, StandardCharsets.UTF_8).split(DELIMITER, -1);
    if (parts.length != FIELD_COUNT) {
      throw new SerializationException("Malformed ReportEntry payload", null);
    }
    return ReportEntry.of(parts[0], parts[1]);
  }

  @Override
  public SerializationFormat format() {
    return SerializationFormat.BINARY;
  }
}
