package io.genfin.money.serialization;

import io.genfin.api.serialization.Codec;
import io.genfin.api.serialization.SerializationFormat;
import io.genfin.money.tax.TaxMetadata;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Encodes the {@link TaxMetadata} attribute bag as delimited key/value pairs. */
public final class TaxMetadataCodec implements Codec<TaxMetadata> {

  private static final String ENTRY_DELIMITER = "";
  private static final String KV_DELIMITER = "";

  @Override
  public byte[] serialize(TaxMetadata value) {
    String text =
        value.attributes().entrySet().stream()
            .map(e -> e.getKey() + KV_DELIMITER + e.getValue())
            .reduce((a, b) -> a + ENTRY_DELIMITER + b)
            .orElse("");
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public TaxMetadata deserialize(byte[] data) {
    String text = new String(data, StandardCharsets.UTF_8);
    if (text.isEmpty()) {
      return TaxMetadata.empty();
    }
    Map<String, String> attributes = new LinkedHashMap<>();
    for (String entry : split(text, ENTRY_DELIMITER)) {
      String[] kv = splitOnce(entry, KV_DELIMITER);
      attributes.put(kv[0], kv.length > 1 ? kv[1] : "");
    }
    return new TaxMetadata(attributes);
  }

  @Override
  public SerializationFormat format() {
    return SerializationFormat.BINARY;
  }

  private static List<String> split(String text, String delimiter) {
    List<String> parts = new ArrayList<>();
    int start = 0;
    int delimiterLength = delimiter.length();
    int index = text.indexOf(delimiter, start);
    while (index >= 0) {
      parts.add(text.substring(start, index));
      start = index + delimiterLength;
      index = text.indexOf(delimiter, start);
    }
    parts.add(text.substring(start));
    return parts;
  }

  private static String[] splitOnce(String text, String delimiter) {
    int index = text.indexOf(delimiter);
    if (index < 0) {
      return new String[] {text};
    }
    return new String[] {text.substring(0, index), text.substring(index + delimiter.length())};
  }
}
