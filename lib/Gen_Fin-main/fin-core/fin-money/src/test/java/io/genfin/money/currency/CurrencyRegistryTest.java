package io.genfin.money.currency;

import static io.genfin.money.support.TestCurrencies.EUR;
import static io.genfin.money.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.money.exception.DuplicateCurrencyException;
import io.genfin.money.exception.UnknownCurrencyException;
import io.genfin.money.port.currency.CurrencyRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CurrencyRegistryTest {

  @Test
  void registerFindAndUnknownLookup() {
    CurrencyRegistry registry = CurrencyRegistries.empty();
    registry.register(USD);

    assertThat(registry.find("USD")).contains(USD);
    assertThat(registry.find("XYZ")).isEmpty();
    assertThatThrownBy(() -> registry.require("XYZ")).isInstanceOf(UnknownCurrencyException.class);
  }

  @Test
  void reRegisteringADifferentCurrencyUnderSameCodeThrows() {
    CurrencyRegistry registry = CurrencyRegistries.empty();
    registry.register(USD);

    Currency conflicting =
        CurrencyFactory.newCurrency()
            .code("USD")
            .symbol("US$")
            .displayName("Different")
            .fractionDigits(2)
            .build();

    assertThatThrownBy(() -> registry.register(conflicting))
        .isInstanceOf(DuplicateCurrencyException.class);
  }

  @Test
  void isoRegistrySeedsManyActiveCurrencies() {
    CurrencyRegistry registry = CurrencyRegistries.iso();

    assertThat(registry.findAllActive()).isNotEmpty();
  }

  @Test
  void concurrentRegistrationIsThreadSafe() throws InterruptedException {
    CurrencyRegistry registry = CurrencyRegistries.empty();
    CountDownLatch done = new CountDownLatch(2);

    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      IntStream.of(0, 1)
          .forEach(
              i ->
                  pool.submit(
                      () -> {
                        registry.register(USD);
                        registry.register(EUR);
                        done.countDown();
                      }));

      assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(registry.findAll()).containsExactlyInAnyOrder(USD, EUR);
  }
}
