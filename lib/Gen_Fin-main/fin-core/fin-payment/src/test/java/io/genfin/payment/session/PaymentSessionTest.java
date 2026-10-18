package io.genfin.payment.session;

import static io.genfin.payment.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.payment.reference.Reference;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentSessionTest {

  @Test
  void builderOpensASession() {
    PaymentSession session =
        SessionBuilder.newSession()
            .amount(Money.of("25.00", USD))
            .expiresAt(Instant.parse("2026-02-01T00:00:00Z"))
            .reference(new SessionReference(Reference.invoice("INV-1")))
            .build();

    assertThat(session.isOpen()).isTrue();
    assertThat(session.state()).isEqualTo(SessionState.OPEN);
  }

  @Test
  void expirationIsRelativeToAsOfInstant() {
    SessionExpiration expiration = new SessionExpiration(Instant.parse("2026-02-01T00:00:00Z"));

    assertThat(expiration.isExpired(Instant.parse("2026-03-01T00:00:00Z"))).isTrue();
    assertThat(expiration.isExpired(Instant.parse("2026-01-01T00:00:00Z"))).isFalse();
  }

  @Test
  void completeCancelExpireReturnNewInstancesWithUpdatedState() {
    PaymentSession session =
        SessionBuilder.newSession()
            .amount(Money.of("25.00", USD))
            .expiresAt(Instant.parse("2026-02-01T00:00:00Z"))
            .reference(new SessionReference(Reference.invoice("INV-1")))
            .build();

    assertThat(session.complete().state()).isEqualTo(SessionState.COMPLETED);
    assertThat(session.cancel().state()).isEqualTo(SessionState.CANCELLED);
    assertThat(session.expire().state()).isEqualTo(SessionState.EXPIRED);
    assertThat(session.state()).isEqualTo(SessionState.OPEN);
  }
}
