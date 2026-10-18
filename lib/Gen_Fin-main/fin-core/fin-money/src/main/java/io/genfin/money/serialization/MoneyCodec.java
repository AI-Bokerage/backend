package io.genfin.money.serialization;

import io.genfin.api.exception.SerializationException;
import io.genfin.api.serialization.Codec;
import io.genfin.api.serialization.SerializationFormat;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import io.genfin.money.port.currency.CurrencyRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * A compact {@code amount|currencyCode} codec. Requires a {@link CurrencyRegistry} to resolve
 * currencies on read.
 */
public final class MoneyCodec implements Codec<Money> {

  private static final int FIELD_COUNT = 2;

  private final CurrencyRegistry currencyRegistry;

  public MoneyCodec(CurrencyRegistry currencyRegistry) {
    this.currencyRegistry = currencyRegistry;
  }

  @Override
  public byte[] serialize(Money value) {
    String text = value.amount().toPlainString() + "|" + value.currency().code();
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public Money deserialize(byte[] data) {
    String text = new String(data, StandardCharsets.UTF_8);
    String[] parts = text.split("\\|", FIELD_COUNT);
    if (parts.length != FIELD_COUNT) {
      throw new SerializationException("Malformed Money payload: " + text, null);
    }
    Currency currency = currencyRegistry.require(parts[1]);
    return Money.of(new BigDecimal(parts[0]), currency);
  }

  @Override
  public SerializationFormat format() {
    return SerializationFormat.BINARY;
  }
}
