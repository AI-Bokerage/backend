package io.genfin.dunning.serialization;

import io.genfin.api.exception.SerializationException;
import io.genfin.api.serialization.Codec;
import io.genfin.api.serialization.SerializationFormat;
import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.collection.CollectionSummary;
import java.nio.charset.StandardCharsets;

/**
 * A compact {@code stage<US>reminderCount<US>retryCount<US>escalationCount<US>historyEntryCount}
 * codec for {@link CollectionSummary}, using the unit separator (U+001F) as delimiter. Mirrors
 * {@code io.genfin.ledger.serialization.ReportEntryCodec} / {@code
 * io.genfin.pricing.serialization.PricingSummaryCodec}.
 */
public final class CollectionSummaryCodec implements Codec<CollectionSummary> {

  private static final String DELIMITER = "";
  private static final int FIELD_COUNT = 5;

  @Override
  public byte[] serialize(CollectionSummary value) {
    String text =
        String.join(
            DELIMITER,
            value.currentStage().name(),
            Long.toString(value.reminderCount()),
            Long.toString(value.retryCount()),
            Long.toString(value.escalationCount()),
            Integer.toString(value.historyEntryCount()));
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public CollectionSummary deserialize(byte[] data) {
    String[] parts = new String(data, StandardCharsets.UTF_8).split(DELIMITER, -1);
    if (parts.length != FIELD_COUNT) {
      throw new SerializationException("Malformed CollectionSummary payload", null);
    }
    try {
      return new CollectionSummary(
          CollectionStage.valueOf(parts[0]),
          Long.parseLong(parts[1]),
          Long.parseLong(parts[2]),
          Long.parseLong(parts[3]),
          Integer.parseInt(parts[4]));
    } catch (IllegalArgumentException e) {
      throw new SerializationException("Malformed CollectionSummary payload", e);
    }
  }

  @Override
  public SerializationFormat format() {
    return SerializationFormat.BINARY;
  }
}
