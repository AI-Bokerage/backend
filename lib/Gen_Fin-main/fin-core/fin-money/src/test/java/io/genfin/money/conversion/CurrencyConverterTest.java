package io.genfin.money.conversion;

import static io.genfin.money.support.TestCurrencies.EUR;
import static io.genfin.money.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.money.exception.UnsupportedConversionException;
import io.genfin.money.internal.conversion.InMemoryExchangeRateProvider;
import io.genfin.money.money.Money;
import io.genfin.money.port.conversion.CurrencyConverter;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CurrencyConverterTest {

  @Test
  void convertsUsingDirectRate() {
    InMemoryExchangeRateProvider rates = CurrencyConverters.inMemoryRates();
    rates.put(new ExchangeRate(USD, EUR, new BigDecimal("0.90"), Instant.EPOCH));
    CurrencyConverter converter = CurrencyConverters.using(rates);

    Money converted =
        Money.of("100.00", USD).convertTo(EUR, converter, ConversionContext.at(Instant.EPOCH));

    assertThat(converted).isEqualTo(Money.of("90.00", EUR));
  }

  @Test
  void convertsUsingInvertedRateWhenOnlyReverseIsKnown() {
    InMemoryExchangeRateProvider rates = CurrencyConverters.inMemoryRates();
    rates.put(new ExchangeRate(EUR, USD, new BigDecimal("1.25"), Instant.EPOCH));
    CurrencyConverter converter = CurrencyConverters.using(rates);

    Money converted =
        Money.of("125.00", USD).convertTo(EUR, converter, ConversionContext.at(Instant.EPOCH));

    assertThat(converted).isEqualTo(Money.of("100.00", EUR));
  }

  @Test
  void sameCurrencyConversionIsIdentity() {
    InMemoryExchangeRateProvider rates = CurrencyConverters.inMemoryRates();
    CurrencyConverter converter = CurrencyConverters.using(rates);

    Money money = Money.of("42.00", USD);

    assertThat(money.convertTo(USD, converter, ConversionContext.at(Instant.EPOCH)))
        .isEqualTo(money);
  }

  @Test
  void missingRateThrowsUnsupportedConversion() {
    CurrencyConverter converter = CurrencyConverters.using(CurrencyConverters.inMemoryRates());

    assertThatThrownBy(
            () ->
                Money.of("1.00", USD)
                    .convertTo(EUR, converter, ConversionContext.at(Instant.EPOCH)))
        .isInstanceOf(UnsupportedConversionException.class);
  }

  @Test
  void addAcrossCurrenciesConvertsExplicitly() {
    InMemoryExchangeRateProvider rates = CurrencyConverters.inMemoryRates();
    rates.put(new ExchangeRate(EUR, USD, new BigDecimal("1.10"), Instant.EPOCH));
    CurrencyConverter converter = CurrencyConverters.using(rates);

    Money total =
        Money.of("10.00", USD)
            .add(Money.of("10.00", EUR), converter, ConversionContext.at(Instant.EPOCH));

    assertThat(total).isEqualTo(Money.of("21.00", USD));
  }
}
