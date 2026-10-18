package io.genfin.money.format;

import static io.genfin.money.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MoneyFormatterTest {

  @Test
  void symbolFirstFormatsAsSymbolThenNumber() {
    String formatted =
        MoneyFormatters.standard()
            .format(
                Money.of("1234.50", USD),
                FormattingContext.of(Locale.US, FormatStyle.SYMBOL_FIRST));

    assertThat(formatted).isEqualTo("$1,234.50");
  }

  @Test
  void isoCodeStyleUsesCurrencyCode() {
    String formatted =
        MoneyFormatters.standard()
            .format(Money.of("10.00", USD), FormattingContext.of(Locale.US, FormatStyle.ISO_CODE));

    assertThat(formatted).isEqualTo("USD 10.00");
  }

  @Test
  void accountingStyleWrapsNegativesInParens() {
    String formatted =
        MoneyFormatters.standard()
            .format(
                Money.of("-10.00", USD), FormattingContext.of(Locale.US, FormatStyle.ACCOUNTING));

    assertThat(formatted).isEqualTo("(10.00 USD)");
  }

  @Test
  void parserRoundTripsIsoFormattedText() {
    String formatted =
        MoneyFormatters.standard()
            .format(Money.of("42.50", USD), FormattingContext.of(Locale.US, FormatStyle.ISO_CODE));

    var parsed =
        MoneyFormatters.standardParser()
            .parse(formatted, USD, FormattingContext.of(Locale.US, FormatStyle.ISO_CODE));

    assertThat(parsed.isSuccess()).isTrue();
    assertThat(parsed.get()).isEqualTo(Money.of("42.50", USD));
  }

  @Test
  void parserHandlesParenthesizedNegatives() {
    var parsed =
        MoneyFormatters.standardParser()
            .parse("(10.00 USD)", USD, FormattingContext.of(Locale.US, FormatStyle.ACCOUNTING));

    assertThat(parsed.get()).isEqualTo(Money.of("-10.00", USD));
  }
}
