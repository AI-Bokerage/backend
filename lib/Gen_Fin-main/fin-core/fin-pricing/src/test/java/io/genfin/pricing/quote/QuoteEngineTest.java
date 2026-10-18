package io.genfin.pricing.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.CatalogId;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.id.PricingResultId;
import io.genfin.pricing.port.quote.QuotePolicy;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.price.PriceType;
import io.genfin.pricing.pricing.PricingMetadata;
import io.genfin.pricing.pricing.PricingRequest;
import io.genfin.pricing.pricing.PricingResult;
import io.genfin.pricing.pricing.PricingSummary;
import io.genfin.pricing.pricing.PricingVersion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the Quote Engine: {@link QuoteBuilder}/{@link QuoteFactory} assemble an immutable
 * {@link Quote} from resolved {@link Price}s, {@link QuotePolicies} resolves how long it stays
 * valid, and {@link Quote}'s lifecycle only allows its structurally valid transitions.
 */
class QuoteEngineTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  @Test
  void builderAssemblesADraftQuoteFromItems() {
    Quote quote =
        QuoteBuilder.forResult(PricingResultId.generate())
            .addItem(CatalogId.generate(), 2, price(100))
            .expiresAt(QuoteExpiration.after(Duration.ofDays(7)))
            .build();

    assertThat(quote.status()).isEqualTo(QuoteStatus.DRAFT);
    assertThat(quote.version()).isEqualTo(QuoteVersion.initial());
    assertThat(quote.summary().netAmount()).isEqualTo(Money.of(100, USD));
    assertThat(quote.isExpiredAt(Instant.now())).isFalse();
  }

  @Test
  void builderRequiresAtLeastOneItemAndAnExpiration() {
    assertThatThrownBy(() -> QuoteBuilder.forResult(PricingResultId.generate()).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void factoryZipsLinesAndPricesAndAsksThePolicyForExpiration() {
    PricingRequest.Line line = new PricingRequest.Line(CatalogId.generate(), 1);
    PricingResult result = resultFor(PricingRequestId.generate());
    QuotePolicy policy = QuotePolicies.validFor(Duration.ofDays(1));

    Quote quote = QuoteFactory.from(result, List.of(line), List.of(price(50)), policy);

    assertThat(quote.items()).hasSize(1);
    assertThat(quote.items().get(0).catalogId()).isEqualTo(line.catalogId());
    assertThat(quote.pricingResultId()).isEqualTo(result.id());
    assertThat(quote.isExpiredAt(Instant.now().plus(Duration.ofDays(2)))).isTrue();
  }

  @Test
  void lifecycleAllowsIssueAcceptButNotAcceptingTwice() {
    Quote draft = draftQuote();

    Quote accepted = draft.issue().accept();

    assertThat(accepted.status()).isEqualTo(QuoteStatus.ACCEPTED);
    assertThatThrownBy(accepted::accept).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void cannotAcceptAnAlreadyExpiredQuote() {
    Quote expired =
        QuoteBuilder.forResult(PricingResultId.generate())
            .addItem(CatalogId.generate(), 1, price(10))
            .expiresAt(QuoteExpiration.at(Instant.now().minusSeconds(1)))
            .build()
            .issue();

    assertThatThrownBy(expired::accept).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void reviseSupersedesWithABumpedVersionAndResetsToDraft() {
    Quote issued = draftQuote().issue();

    Quote revised =
        issued.revise(
            List.of(QuoteItem.of(new PricingRequest.Line(CatalogId.generate(), 1), price(20))),
            QuoteExpiration.after(Duration.ofDays(1)));

    assertThat(revised.status()).isEqualTo(QuoteStatus.DRAFT);
    assertThat(revised.version()).isEqualTo(new QuoteVersion(2));
    assertThat(revised.id()).isEqualTo(issued.id());
  }

  private static Quote draftQuote() {
    return QuoteBuilder.forResult(PricingResultId.generate())
        .addItem(CatalogId.generate(), 1, price(100))
        .expiresAt(QuoteExpiration.after(Duration.ofDays(7)))
        .build();
  }

  private static Price price(long amount) {
    return new Price(
        CatalogId.generate(),
        PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", Money.of(amount, USD))));
  }

  private static PricingResult resultFor(PricingRequestId requestId) {
    Money base = Money.of(50, USD);
    return new PricingResult(
        PricingResultId.generate(),
        requestId,
        PricingVersion.initial(),
        new PricingSummary(base, Money.zero(USD), base),
        PricingMetadata.empty(),
        Instant.now());
  }
}
