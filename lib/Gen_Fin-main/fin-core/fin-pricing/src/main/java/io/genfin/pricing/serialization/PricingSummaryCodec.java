package io.genfin.pricing.serialization;

import io.genfin.api.exception.SerializationException;
import io.genfin.api.serialization.Codec;
import io.genfin.api.serialization.SerializationFormat;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import io.genfin.money.port.currency.CurrencyRegistry;
import io.genfin.pricing.pricing.PricingSummary;
import java.nio.charset.StandardCharsets;

/**
 * A compact {@code amount<US>currency<US>amount<US>currency<US>amount<US>currency} codec for {@link
 * PricingSummary} (base/reduction/net, in that order), using the unit separator (U+001F) as
 * delimiter. Mirrors {@code io.genfin.ledger.serialization.ReportEntryCodec}.
 */
public final class PricingSummaryCodec implements Codec<PricingSummary> {

  private static final String DELIMITER = "";
  private static final int FIELD_COUNT = 6;

  private final CurrencyRegistry currencyRegistry;

  public PricingSummaryCodec(CurrencyRegistry currencyRegistry) {
    this.currencyRegistry = currencyRegistry;
  }

  @Override
  public byte[] serialize(PricingSummary value) {
    String text =
        String.join(
            DELIMITER,
            value.baseAmount().amount().toPlainString(),
            value.baseAmount().currency().code(),
            value.reductionAmount().amount().toPlainString(),
            value.reductionAmount().currency().code(),
            value.netAmount().amount().toPlainString(),
            value.netAmount().currency().code());
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public PricingSummary deserialize(byte[] data) {
    String[] parts = new String(data, StandardCharsets.UTF_8).split(DELIMITER, -1);
    if (parts.length != FIELD_COUNT) {
      throw new SerializationException("Malformed PricingSummary payload", null);
    }
    Money base = money(parts[0], parts[1]);
    Money reduction = money(parts[2], parts[3]);
    Money net = money(parts[4], parts[5]);
    return new PricingSummary(base, reduction, net);
  }

  private Money money(String amount, String currencyCode) {
    Currency currency = currencyRegistry.require(currencyCode);
    return Money.of(amount, currency);
  }

  @Override
  public SerializationFormat format() {
    return SerializationFormat.BINARY;
  }
}
