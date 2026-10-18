package io.genfin.pricing.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.time.ClockProviders;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.pricing.event.PricingCompleted;
import io.genfin.pricing.event.PricingRejected;
import io.genfin.pricing.event.PricingStarted;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.id.PricingResultId;
import io.genfin.pricing.lifecycle.PricingRequestStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricingRequestTest {

  private static final ClockProvider CLOCK =
      ClockProviders.fixed(Instant.parse("2026-01-01T00:00:00Z"));
  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  private static PricingRequest newRequest() {
    return new PricingRequest(
        PricingRequestId.generate(),
        List.of(new PricingRequest.Line(CatalogId.generate(), 2)),
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void startsCreatedAndFollowsTheHappyPathToPricedRecordingEvents() {
    PricingRequest request = newRequest();
    assertThat(request.status()).isEqualTo(PricingRequestStatus.CREATED);

    request.startValidating(CLOCK);
    assertThat(request.status()).isEqualTo(PricingRequestStatus.VALIDATING);

    request.startCalculating();
    assertThat(request.status()).isEqualTo(PricingRequestStatus.CALCULATING);

    request.complete(PricingResultId.generate(), Money.zero(USD), CLOCK);
    assertThat(request.status()).isEqualTo(PricingRequestStatus.PRICED);

    assertThat(request.pullEvents())
        .hasSize(2)
        .satisfiesExactly(
            e -> assertThat(e).isInstanceOf(PricingStarted.class),
            e -> assertThat(e).isInstanceOf(PricingCompleted.class));
  }

  @Test
  void cannotSkipStraightFromCreatedToCalculating() {
    PricingRequest request = newRequest();

    assertThatThrownBy(request::startCalculating).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectIsOnlyAllowedWhileInFlightAndRecordsAnEvent() {
    PricingRequest request = newRequest();

    assertThatThrownBy(() -> request.reject("bad input", CLOCK))
        .isInstanceOf(IllegalStateException.class);

    request.startValidating(CLOCK);
    request.pullEvents();
    request.reject("bad input", CLOCK);

    assertThat(request.status()).isEqualTo(PricingRequestStatus.REJECTED);
    assertThatThrownBy(request::expire).isInstanceOf(IllegalStateException.class);
    assertThat(request.pullEvents()).anyMatch(PricingRejected.class::isInstance);
  }
}
