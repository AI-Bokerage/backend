package io.genfin.ledger.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.JournalEntryId;
import io.genfin.ledger.id.JournalLineId;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.lifecycle.StandardLedgerStatus;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

class JournalEntryTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  private static JournalEntry balancedEntry() {
    return new JournalEntry(
        JournalEntryId.generate(),
        LedgerId.generate(),
        StandardJournalType.STANDARD,
        List.of(
            JournalLine.debit(
                JournalLineId.generate(), AccountId.generate(), Money.of("10.00", USD)),
            JournalLine.credit(
                JournalLineId.generate(), AccountId.generate(), Money.of("10.00", USD))));
  }

  @Test
  void newJournalEntryStartsCreated() {
    JournalEntry entry = balancedEntry();

    assertThat(entry.status()).isEqualTo(StandardLedgerStatus.CREATED);
    assertThat(entry.history().transitions()).containsExactly(StandardLedgerStatus.CREATED);
    assertThat(entry.version()).isEqualTo(JournalVersion.initial());
  }

  @Test
  void rejectsAnUnbalancedSetOfLines() {
    assertThatThrownBy(
            () ->
                new JournalEntry(
                    JournalEntryId.generate(),
                    LedgerId.generate(),
                    StandardJournalType.STANDARD,
                    List.of(
                        JournalLine.debit(
                            JournalLineId.generate(), AccountId.generate(), Money.of("10.00", USD)),
                        JournalLine.credit(
                            JournalLineId.generate(),
                            AccountId.generate(),
                            Money.of("9.00", USD)))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsFewerThanTwoLines() {
    assertThatThrownBy(
            () ->
                new JournalEntry(
                    JournalEntryId.generate(),
                    LedgerId.generate(),
                    StandardJournalType.STANDARD,
                    List.of(
                        JournalLine.debit(
                            JournalLineId.generate(),
                            AccountId.generate(),
                            Money.of("10.00", USD)))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void progressesThroughTheStandardPostingLifecycle() {
    JournalEntry entry = balancedEntry();

    entry.validate();
    entry.post();
    entry.settle();

    assertThat(entry.status()).isEqualTo(StandardLedgerStatus.SETTLED);
    assertThat(entry.history().transitions())
        .containsExactly(
            StandardLedgerStatus.CREATED,
            StandardLedgerStatus.VALIDATED,
            StandardLedgerStatus.POSTED,
            StandardLedgerStatus.SETTLED);
    assertThat(entry.version()).isEqualTo(new JournalVersion(4));
  }

  @Test
  void reversalIsAForwardTransitionNotADeletion() {
    JournalEntry entry = balancedEntry();
    entry.validate();
    entry.post();
    List<JournalLine> originalLines = entry.lines();

    entry.reverse();

    assertThat(entry.status()).isEqualTo(StandardLedgerStatus.REVERSED);
    assertThat(entry.history().transitions()).hasSize(4);
    assertThat(entry.history().transitions().get(2)).isEqualTo(StandardLedgerStatus.POSTED);
    assertThat(entry.lines()).isEqualTo(originalLines);
  }

  @Test
  void rejectsAnEventThatIsNotValidFromTheCurrentState() {
    JournalEntry entry = balancedEntry();

    assertThatThrownBy(entry::settle).isInstanceOf(IllegalStateException.class);
  }
}
