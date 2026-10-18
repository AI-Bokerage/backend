package io.genfin.ledger.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.event.OccurredAt;
import io.genfin.ledger.fact.FinancialFact;
import io.genfin.ledger.fact.FinancialFactType;
import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.journal.JournalAttributes;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.port.posting.PostingEngine;
import io.genfin.ledger.port.posting.PostingPolicy;
import io.genfin.ledger.port.posting.PostingStrategy;
import io.genfin.ledger.port.posting.PostingValidator;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.refund.reference.Reference;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostingEngineTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  private static final AccountId CASH = AccountId.generate();
  private static final AccountId RECEIVABLE = AccountId.generate();

  private static FinancialFact fact(Money amount) {
    return FinancialFact.of(
        amount,
        Reference.payment("pay-1"),
        FinancialFactType.of("INVOICE_PAID"),
        new OccurredAt(Instant.parse("2026-07-31T00:00:00Z")));
  }

  /** A strategy that debits CASH and credits RECEIVABLE for the fact's full amount. */
  private static PostingStrategy balancedStrategy() {
    return new PostingStrategy() {
      @Override
      public boolean supports(FinancialFact fact) {
        return "INVOICE_PAID".equals(fact.factType().code());
      }

      @Override
      public List<PostingEntry> resolve(FinancialFact fact, PostingContext context) {
        return List.of(
            PostingEntry.of(CASH, Debit.of(fact.amount())),
            PostingEntry.of(RECEIVABLE, Credit.of(fact.amount())));
      }
    };
  }

  /** A misconfigured strategy that only debits CASH, never balancing the posting. */
  private static PostingStrategy unbalancedStrategy() {
    return new PostingStrategy() {
      @Override
      public boolean supports(FinancialFact fact) {
        return true;
      }

      @Override
      public List<PostingEntry> resolve(FinancialFact fact, PostingContext context) {
        return List.of(PostingEntry.of(CASH, Debit.of(fact.amount())));
      }
    };
  }

  @Test
  void postsABalancedFactAsASystemGeneratedJournalEntry() {
    PostingEngine engine =
        PostingEngines.of(
            PostingPolicies.of(List.of(balancedStrategy())), PostingValidators.standard());

    JournalEntry entry =
        engine.post(
            fact(Money.of("10.00", USD)),
            LedgerId.generate(),
            PostingContext.at(Instant.parse("2026-07-31T00:00:00Z")));

    assertThat(entry.lines()).hasSize(2);
    Money totalDebit =
        entry.lines().stream()
            .map(io.genfin.ledger.journal.JournalLine::debit)
            .reduce(Money.zero(USD), Money::add);
    Money totalCredit =
        entry.lines().stream()
            .map(io.genfin.ledger.journal.JournalLine::credit)
            .reduce(Money.zero(USD), Money::add);
    assertThat(totalDebit).isEqualTo(totalCredit).isEqualTo(Money.of("10.00", USD));
    assertThat(entry.attributes()).isEqualTo(JournalAttributes.systemPosted());
    assertThat(entry.references().all()).containsExactly(Reference.payment("pay-1"));
  }

  @Test
  void rejectsAnUnbalancedResolutionInsteadOfBuildingAJournalEntry() {
    PostingEngine engine =
        PostingEngines.of(
            PostingPolicies.of(List.of(unbalancedStrategy())), PostingValidators.standard());
    PostingContext context = PostingContext.at(Instant.parse("2026-07-31T00:00:00Z"));

    assertThatThrownBy(
            () -> engine.post(fact(Money.of("10.00", USD)), LedgerId.generate(), context))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unbalanced");
  }

  @Test
  void policyFailsClearlyWhenNoStrategySupportsTheFact() {
    PostingPolicy policy = PostingPolicies.empty();
    PostingContext context = PostingContext.at(Instant.parse("2026-07-31T00:00:00Z"));

    assertThatThrownBy(() -> policy.resolve(fact(Money.of("10.00", USD)), context))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void validatorRejectsASingleSidedPosting() {
    PostingValidator validator = PostingValidators.standard();
    PostingContext context = PostingContext.at(Instant.parse("2026-07-31T00:00:00Z"));

    PostingValidationResult result =
        validator.validate(
            List.of(PostingEntry.of(CASH, Debit.of(Money.of("5.00", USD)))), context);

    assertThat(result.isValid()).isFalse();
  }

  @Test
  void calculatorRollsUpBalancesPerAccount() {
    List<PostingEntry> entries =
        List.of(
            PostingEntry.of(CASH, Debit.of(Money.of("10.00", USD))),
            PostingEntry.of(RECEIVABLE, Credit.of(Money.of("10.00", USD))));

    List<PostingBalance> balances = PostingCalculator.balances(entries, USD);

    assertThat(balances).hasSize(2);
    assertThat(PostingCalculator.isBalanced(entries, USD)).isTrue();
  }
}
