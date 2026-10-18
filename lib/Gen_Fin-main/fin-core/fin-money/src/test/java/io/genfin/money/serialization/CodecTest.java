package io.genfin.money.serialization;

import static io.genfin.money.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyRegistries;
import io.genfin.money.money.Money;
import io.genfin.money.port.currency.CurrencyRegistry;
import io.genfin.money.tax.TaxMetadata;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CodecTest {

  @Test
  void moneyRoundTripsThroughCodec() {
    CurrencyRegistry registry = CurrencyRegistries.empty();
    registry.register(USD);
    MoneyCodec codec = new MoneyCodec(registry);

    Money original = Money.of("1234.56", USD);
    byte[] bytes = codec.serialize(original);

    assertThat(codec.deserialize(bytes)).isEqualTo(original);
  }

  @Test
  void currencyRoundTripsThroughCodec() {
    CurrencyCodec codec = new CurrencyCodec();

    byte[] bytes = codec.serialize(USD);

    Currency roundTripped = codec.deserialize(bytes);
    assertThat(roundTripped.code()).isEqualTo("USD");
    assertThat(roundTripped.fractionDigits()).isEqualTo(2);
    assertThat(roundTripped.symbol()).isEqualTo("$");
  }

  @Test
  void taxMetadataRoundTripsThroughCodec() {
    TaxMetadataCodec codec = new TaxMetadataCodec();
    TaxMetadata original =
        new TaxMetadata(Map.of("placeOfSupply", "MH", "gstin", "27ABCDE1234F1Z5"));

    byte[] bytes = codec.serialize(original);

    assertThat(codec.deserialize(bytes).attributes()).isEqualTo(original.attributes());
  }

  @Test
  void emptyTaxMetadataRoundTrips() {
    TaxMetadataCodec codec = new TaxMetadataCodec();

    assertThat(codec.deserialize(codec.serialize(TaxMetadata.empty())))
        .isEqualTo(TaxMetadata.empty());
  }
}
