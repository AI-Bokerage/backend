package io.genfin.money.internal.currency;

import io.genfin.money.currency.Currency;
import io.genfin.money.exception.DuplicateCurrencyException;
import io.genfin.money.port.currency.CurrencyRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultCurrencyRegistry implements CurrencyRegistry {

  private final ConcurrentMap<String, Currency> currencies = new ConcurrentHashMap<>();

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void register(Currency currency) {
    // Currency#equals compares by code alone (correct value-object identity), so it cannot detect a
    // genuinely different definition registered under the same code — only reference identity can.
    Currency existing = currencies.putIfAbsent(currency.code(), currency);
    if (existing != null
        && existing != currency) { // NOPMD - deliberate reference check, see comment above
      throw new DuplicateCurrencyException(currency.code());
    }
  }

  @Override
  public Optional<Currency> find(String code) {
    return Optional.ofNullable(currencies.get(code));
  }

  @Override
  public List<Currency> findAll() {
    return List.copyOf(currencies.values());
  }
}
