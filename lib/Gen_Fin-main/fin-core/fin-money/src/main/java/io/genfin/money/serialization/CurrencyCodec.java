package io.genfin.money.serialization;

import io.genfin.api.exception.SerializationException;
import io.genfin.api.serialization.Codec;
import io.genfin.api.serialization.SerializationFormat;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Self-contained {@code
 * code<US>numericCode<US>symbol<US>displayName<US>fractionDigits<US>localeTag<US>active} codec.
 */
public final class CurrencyCodec implements Codec<Currency> {

  private static final int FIELD_COUNT = 7;
  private static final String DELIMITER = "";

  @Override
  public byte[] serialize(Currency value) {
    String text =
        String.join(
            DELIMITER,
            value.code(),
            value.numericCode().map(String::valueOf).orElse(""),
            value.symbol(),
            value.displayName(),
            String.valueOf(value.fractionDigits()),
            value.locale().map(Locale::toLanguageTag).orElse(""),
            String.valueOf(value.active()));
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Currency deserialize(byte[] data) {
    String[] parts = new String(data, StandardCharsets.UTF_8).split(DELIMITER, -1);
    if (parts.length != FIELD_COUNT) {
      throw new SerializationException("Malformed Currency payload", null);
    }
    CurrencyFactory factory =
        CurrencyFactory.newCurrency()
            .code(parts[0])
            .symbol(parts[2])
            .displayName(parts[3])
            .fractionDigits(Integer.parseInt(parts[4]))
            .active(Boolean.parseBoolean(parts[6]));
    if (!parts[1].isEmpty()) {
      factory.numericCode(Integer.parseInt(parts[1]));
    }
    if (!parts[5].isEmpty()) {
      factory.locale(Locale.forLanguageTag(parts[5]));
    }
    return factory.build();
  }

  @Override
  public SerializationFormat format() {
    return SerializationFormat.BINARY;
  }
}
