package io.genfin.providerapi.checkout;

import static io.genfin.providerapi.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import java.net.URI;
import org.junit.jupiter.api.Test;

class CheckoutSessionTest {

  @Test
  void redirectIsPresentForRedirectModeSessions() {
    CheckoutSession session =
        new CheckoutSession(
            "sess-1",
            CheckoutMode.REDIRECT,
            URI.create("https://pay.example"),
            Money.of("10.00", USD),
            null);

    assertThat(session.redirect()).isPresent();
  }

  @Test
  void redirectIsAbsentForEmbeddedSessions() {
    CheckoutSession session =
        new CheckoutSession("sess-2", CheckoutMode.EMBEDDED, null, Money.of("10.00", USD), null);

    assertThat(session.redirect()).isEmpty();
  }
}
