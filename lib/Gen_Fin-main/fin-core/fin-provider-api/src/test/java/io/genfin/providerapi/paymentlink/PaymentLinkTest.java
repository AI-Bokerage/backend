package io.genfin.providerapi.paymentlink;

import static io.genfin.providerapi.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentLinkTest {

  @Test
  void nullMetadataDefaultsToEmpty() {
    PaymentLink link =
        new PaymentLink(
            "link-1",
            URI.create("https://pay.example/link-1"),
            Money.of("10.00", USD),
            Instant.now(),
            PaymentLinkStatus.ACTIVE,
            null);

    assertThat(link.metadata().values()).isEmpty();
    assertThat(link.status()).isEqualTo(PaymentLinkStatus.ACTIVE);
  }
}
