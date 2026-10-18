package io.genfin.money.port.format;

import io.genfin.api.port.spi.Extension;
import io.genfin.api.result.Result;
import io.genfin.money.currency.Currency;
import io.genfin.money.format.FormattingContext;
import io.genfin.money.money.Money;

public interface MoneyParser extends Extension {

  Result<Money> parse(String text, Currency currencyHint, FormattingContext context);
}
