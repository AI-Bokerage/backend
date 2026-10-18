package io.genfin.ledger.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.event.OccurredAt;
import io.genfin.ledger.fact.FinancialFact;
import io.genfin.ledger.fact.FinancialFactType;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.port.posting.PostingRuleRegistry;
import io.genfin.ledger.port.posting.PostingRuleResolver;
import io.genfin.ledger.port.posting.PostingStrategy;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.refund.reference.Reference;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises the registry/resolver wiring only. The example {@link PostingStrategy} below is
 * test-only scaffolding, not a shipped default - Gen-Fin registers no real posting mappings of its
 * own.
 */
class PostingRuleResolverTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();
  private static final FinancialFactType INVOICE_PAID = FinancialFactType.of("INVOICE_PAID");
  private static final AccountId CASH = AccountId.generate();
  private static final AccountId RECEIVABLE = AccountId.generate();

  private static FinancialFact fact(FinancialFactType type) {
    return FinancialFact.of(
        Money.of("10.00", USD),
        Reference.payment("pay-1"),
        type,
        new OccurredAt(Instant.parse("2026-07-31T00:00:00Z")));
  }

  /**
   * Example rule only, kept out of main sources: any fact of this type debits CASH / credits AR.
   */
  private static PostingStrategy exampleRule() {
    return new PostingStrategy() {
      @Override
      public boolean supports(FinancialFact fact) {
        return true;
      }

      @Override
      public List<PostingEntry> resolve(FinancialFact fact, PostingContext context) {
        return List.of(
            PostingEntry.of(CASH, Debit.of(fact.amount())),
            PostingEntry.of(RECEIVABLE, Credit.of(fact.amount())));
      }
    };
  }

  @Test
  void resolvesThroughTheKeyedRuleSetRegisteredForTheFactType() {
    PostingRuleRegistry registry = PostingRuleRegistries.empty();
    registry.register(PostingRuleSet.of(INVOICE_PAID, exampleRule()));
    PostingRuleResolver resolver = PostingRuleResolvers.of(registry);

    List<PostingEntry> entries =
        resolver.resolve(
            fact(INVOICE_PAID), PostingContext.at(Instant.parse("2026-07-31T00:00:00Z")));

    assertThat(entries).hasSize(2);
  }

  @Test
  void failsClearlyWhenNoRuleSetIsRegisteredForTheFactType() {
    PostingRuleResolver resolver = PostingRuleResolvers.of(PostingRuleRegistries.empty());

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    fact(INVOICE_PAID), PostingContext.at(Instant.parse("2026-07-31T00:00:00Z"))))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void failsClearlyWhenNoRuleInTheRegisteredSetSupportsTheFact() {
    PostingStrategy neverSupports =
        new PostingStrategy() {
          @Override
          public boolean supports(FinancialFact fact) {
            return false;
          }

          @Override
          public List<PostingEntry> resolve(FinancialFact fact, PostingContext context) {
            throw new AssertionError("must not be called");
          }
        };
    PostingRuleRegistry registry = PostingRuleRegistries.empty();
    registry.register(PostingRuleSet.of(INVOICE_PAID, neverSupports));
    PostingRuleResolver resolver = PostingRuleResolvers.of(registry);

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    fact(INVOICE_PAID), PostingContext.at(Instant.parse("2026-07-31T00:00:00Z"))))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void aRuleSetMustCarryAtLeastOneRule() {
    assertThatThrownBy(() -> PostingRuleSet.of(INVOICE_PAID))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
