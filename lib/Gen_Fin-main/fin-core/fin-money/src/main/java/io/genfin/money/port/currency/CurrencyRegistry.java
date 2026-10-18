package io.genfin.money.port.currency;

import io.genfin.money.currency.Currency;
import java.util.List;
import java.util.Optional;

/**
 * A discoverable source of {@link Currency} instances. Never hardcode currencies — look them up
 * here.
 */
public interface CurrencyRegistry {

  void register(Currency currency);

  Optional<Currency> find(String code);

  default Currency require(String code) {
    return find(code)
        .orElseThrow(() -> new io.genfin.money.exception.UnknownCurrencyException(code));
  }

  List<Currency> findAll();

  default List<Currency> findAllActive() {
    return findAll().stream().filter(Currency::active).toList();
  }
}
