package io.genfin.ledger.reversal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.JournalEntryId;
import io.genfin.ledger.id.JournalLineId;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.journal.JournalAttributes;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.journal.StandardJournalType;
import io.genfin.ledger.lifecycle.StandardLedgerStatus;
import io.genfin.ledger.port.reversal.ReverseJournal;
import io.genfin.ledger.posting.Credit;
import io.genfin.ledger.posting.Debit;
import io.genfin.ledger.posting.PostingEntry;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReverseJournalTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();
  private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
  private static final AccountId CASH = AccountId.generate();
  private static final AccountId RECEIVABLE = AccountId.generate();

  private static final ReverseJournal ENGINE = ReverseJournals.standard();

  private static JournalEntry postedEntry() {
    JournalEntry entry =
        new JournalEntry(
            JournalEntryId.generate(),
            LedgerId.generate(),
            StandardJournalType.STANDARD,
            List.of(
                JournalLine.debit(JournalLineId.generate(), CASH, Money.of("10.00", USD)),
                JournalLine.credit(JournalLineId.generate(), RECEIVABLE, Money.of("10.00", USD))));
    entry.validate();
    entry.post();
    return entry;
  }

  @Test
  void reversalSwapsEveryLineAndKeepsTheOriginalEntryIntact() {
    JournalEntry original = postedEntry();
    List<JournalLine> originalLines = original.lines();

    Reversal reversal =
        ENGINE.reverse(original, StandardReversalReason.DATA_ENTRY_ERROR, "oops", NOW);

    assertThat(original.status()).isEqualTo(StandardLedgerStatus.REVERSED);
    assertThat(original.lines()).isEqualTo(originalLines);

    JournalEntry reversingEntry = reversal.reversingEntry();
    assertThat(reversingEntry.type()).isEqualTo(StandardJournalType.REVERSAL);
    assertThat(reversingEntry.lines()).hasSize(2);
    Money totalDebit =
        reversingEntry.lines().stream().map(JournalLine::debit).reduce(Money.zero(USD), Money::add);
    Money totalCredit =
        reversingEntry.lines().stream()
            .map(JournalLine::credit)
            .reduce(Money.zero(USD), Money::add);
    assertThat(totalDebit).isEqualTo(totalCredit).isEqualTo(Money.of("10.00", USD));

    JournalLine reversedCash =
        reversingEntry.lines().stream().filter(l -> l.accountId().equals(CASH)).findFirst().get();
    assertThat(reversedCash.isDebit()).isFalse();
    assertThat(reversedCash.credit()).isEqualTo(Money.of("10.00", USD));
  }

  @Test
  void reversalRejectsAnUnregisteredReason() {
    JournalEntry original = postedEntry();
    ReversalReason unknown = () -> "NOT_REGISTERED";

    assertThatThrownBy(() -> ENGINE.reverse(original, unknown, "memo", NOW))
        .isInstanceOf(RuntimeException.class);
    assertThat(original.status()).isEqualTo(StandardLedgerStatus.POSTED);
  }

  @Test
  void reversalRejectsAnEntryMarkedNotReversible() {
    JournalEntry original =
        new JournalEntry(
            JournalEntryId.generate(),
            LedgerId.generate(),
            StandardJournalType.STANDARD,
            List.of(
                JournalLine.debit(JournalLineId.generate(), CASH, Money.of("10.00", USD)),
                JournalLine.credit(JournalLineId.generate(), RECEIVABLE, Money.of("10.00", USD))),
            io.genfin.ledger.lifecycle.LedgerLifecycles.created(),
            io.genfin.refund.reference.ReferenceCollection.empty(),
            io.genfin.ledger.journal.JournalMetadata.empty(),
            new JournalAttributes(false, false, false));
    original.validate();
    original.post();

    assertThatThrownBy(
            () -> ENGINE.reverse(original, StandardReversalReason.SYSTEM_ERROR, "memo", NOW))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void adjustmentPostsSuppliedCorrectiveLegsAndMovesTheOriginalToAdjusted() {
    JournalEntry original = postedEntry();

    Adjustment adjustment =
        ENGINE.adjust(
            original,
            List.of(
                PostingEntry.of(CASH, Debit.of(Money.of("2.00", USD))),
                PostingEntry.of(RECEIVABLE, Credit.of(Money.of("2.00", USD)))),
            StandardReversalReason.AUDIT_ADJUSTMENT,
            "period-end true-up",
            NOW);

    assertThat(original.status()).isEqualTo(StandardLedgerStatus.ADJUSTED);
    assertThat(adjustment.adjustingEntry().type()).isEqualTo(StandardJournalType.ADJUSTMENT);
    assertThat(adjustment.adjustingEntry().lines()).hasSize(2);
  }

  @Test
  void correctionReversesTheOriginalAndPostsTheReplacementAsANewStandardEntry() {
    JournalEntry original = postedEntry();

    Correction correction =
        ENGINE.correct(
            original,
            List.of(
                PostingEntry.of(CASH, Debit.of(Money.of("15.00", USD))),
                PostingEntry.of(RECEIVABLE, Credit.of(Money.of("15.00", USD)))),
            StandardReversalReason.INCORRECT_AMOUNT,
            "should have been 15.00",
            NOW);

    assertThat(original.status()).isEqualTo(StandardLedgerStatus.REVERSED);
    assertThat(correction.reversal().original()).isEqualTo(original);
    assertThat(correction.correctingEntry().type()).isEqualTo(StandardJournalType.STANDARD);
    Money total =
        correction.correctingEntry().lines().stream()
            .map(JournalLine::debit)
            .reduce(Money.zero(USD), Money::add);
    assertThat(total).isEqualTo(Money.of("15.00", USD));
    // Three entries now exist side by side: original, reversal, correction - none deleted.
    assertThat(correction.correctingEntry().id()).isNotEqualTo(original.id());
    assertThat(correction.reversal().reversingEntry().id()).isNotEqualTo(original.id());
  }
}
