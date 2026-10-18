package io.genfin.money.port.currency;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.currency.Currency;
import java.util.List;

/**
 * Pluggable bulk source of currencies (ISO catalog, crypto catalog, enterprise ledger, ...),
 * registered as an {@link Extension}.
 */
public interface CurrencyProvider extends Extension {

  List<Currency> provide();
}
