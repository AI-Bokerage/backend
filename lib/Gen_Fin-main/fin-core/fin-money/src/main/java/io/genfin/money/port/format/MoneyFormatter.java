package io.genfin.money.port.format;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.format.FormattingContext;
import io.genfin.money.money.Money;

public interface MoneyFormatter extends Extension {

  String format(Money money, FormattingContext context);
}
