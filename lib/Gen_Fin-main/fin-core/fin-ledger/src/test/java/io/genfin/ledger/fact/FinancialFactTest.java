package io.genfin.ledger.fact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.event.OccurredAt;
import io.genfin.api.exception.ValidationException;
import io.genfin.api.time.ClockProviders;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.refund.reference.Reference;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FinancialFactTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void normalizesFactsFromAnyUpstreamModuleIntoOneShape() {
    var usd =
        CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();
    FinancialFact fact =
        FinancialFact.of(
            Money.of("10.00", usd),
            Reference.payment("payment-1"),
            FinancialFactType.of("PAYMENT_CAPTURED"),
            OccurredAt.now(ClockProviders.fixed(NOW)));

    assertThat(fact.amount().currency().code()).isEqualTo("USD");
    assertThat(fact.reference().value().value()).isEqualTo("payment-1");
    assertThat(fact.factType().code()).isEqualTo("PAYMENT_CAPTURED");
    assertThat(fact.metadata()).isEmpty();
  }

  @Test
  void rejectsMissingAmount() {
    assertThatThrownBy(
            () ->
                FinancialFact.of(
                    null,
                    Reference.payment("payment-1"),
                    FinancialFactType.of("PAYMENT_CAPTURED"),
                    OccurredAt.now(ClockProviders.fixed(NOW))))
        .isInstanceOf(ValidationException.class);
  }
}
