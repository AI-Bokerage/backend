package io.genfin.pricing.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.id.PricingResultId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Unit tests for the immutable {@link PricingResult} and its constituent value objects. */
class PricingResultTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  @Test
  void rejectsNullConstituents() {
    PricingResultId id = PricingResultId.generate();
    PricingRequestId requestId = PricingRequestId.generate();
    PricingVersion version = PricingVersion.initial();
    PricingSummary summary = new PricingSummary(Money.zero(USD), Money.zero(USD), Money.zero(USD));
    PricingMetadata metadata = PricingMetadata.empty();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    assertThatThrownBy(() -> new PricingResult(null, requestId, version, summary, metadata, now))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new PricingResult(id, null, version, summary, metadata, now))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new PricingResult(id, requestId, null, summary, metadata, now))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new PricingResult(id, requestId, version, null, metadata, now))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new PricingResult(id, requestId, version, summary, null, now))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new PricingResult(id, requestId, version, summary, metadata, null))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void isAValueObjectEqualByItsFields() {
    PricingResultId id = PricingResultId.generate();
    PricingRequestId requestId = PricingRequestId.generate();
    PricingVersion version = PricingVersion.initial();
    PricingSummary summary =
        new PricingSummary(Money.of(100, USD), Money.of(-10, USD), Money.of(90, USD));
    PricingMetadata metadata = PricingMetadata.empty();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    PricingResult first = new PricingResult(id, requestId, version, summary, metadata, now);
    PricingResult second = new PricingResult(id, requestId, version, summary, metadata, now);

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }

  @Test
  void versionStartsAtOneAndIncrementsWithoutMutatingThePrevious() {
    PricingVersion initial = PricingVersion.initial();
    PricingVersion next = initial.next();

    assertThat(initial.number()).isEqualTo(1);
    assertThat(next.number()).isEqualTo(2);
    assertThatThrownBy(() -> new PricingVersion(-1)).isInstanceOf(ValidationException.class);
  }

  @Test
  void metadataIsImmutableAndAdditive() {
    PricingMetadata empty = PricingMetadata.empty();
    PricingMetadata withSource = empty.with("source", "quote-conversion");

    assertThat(empty.find("source")).isEmpty();
    assertThat(withSource.find("source")).contains("quote-conversion");
  }

  @Test
  void summaryRejectsNullAmounts() {
    Money zero = Money.zero(USD);
    assertThatThrownBy(() -> new PricingSummary(null, zero, zero))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new PricingSummary(zero, null, zero))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> new PricingSummary(zero, zero, null))
        .isInstanceOf(ValidationException.class);
  }
}
