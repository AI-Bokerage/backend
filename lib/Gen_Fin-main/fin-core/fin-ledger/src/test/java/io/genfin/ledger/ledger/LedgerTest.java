package io.genfin.ledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.ledger.id.AccountId;
import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.ledger.id.BalanceSnapshotId;
import io.genfin.ledger.id.ChartOfAccountsId;
import io.genfin.ledger.id.JournalEntryId;
import io.genfin.ledger.id.JournalLineId;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.journal.JournalLine;
import io.genfin.ledger.journal.StandardJournalType;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency().code("USD").symbol("$").displayName("US Dollar").build();

  @Test
  void newLedgerOwnsOnlyItsChartOfAccountsAndStartsEmpty() {
    ChartOfAccountsId chartOfAccountsId = ChartOfAccountsId.generate();
    Ledger ledger = new Ledger(LedgerId.generate(), chartOfAccountsId);

    assertThat(ledger.chartOfAccountsId()).isEqualTo(chartOfAccountsId);
    assertThat(ledger.journalEntries()).isEmpty();
    assertThat(ledger.balanceSnapshots()).isEmpty();
    assertThat(ledger.accountingPeriods()).isEmpty();
    assertThat(ledger.history()).containsExactly("OPENED");
    assertThat(ledger.summary()).isEqualTo(new Ledger.Summary(0, 0, 0));
  }

  @Test
  void recordingActivityAccumulatesCollectionsAndHistory() {
    Ledger ledger = new Ledger(LedgerId.generate(), ChartOfAccountsId.generate());
    JournalEntryId journalEntryId = JournalEntryId.generate();
    BalanceSnapshotId balanceSnapshotId = BalanceSnapshotId.generate();
    AccountingPeriodId accountingPeriodId = AccountingPeriodId.generate();

    ledger.recordJournalEntry(journalEntryId);
    ledger.recordBalanceSnapshot(balanceSnapshotId);
    ledger.openPeriod(accountingPeriodId);

    assertThat(ledger.journalEntries()).containsExactly(journalEntryId);
    assertThat(ledger.balanceSnapshots()).containsExactly(balanceSnapshotId);
    assertThat(ledger.accountingPeriods()).containsExactly(accountingPeriodId);
    assertThat(ledger.history()).hasSize(3);
    assertThat(ledger.summary()).isEqualTo(new Ledger.Summary(1, 1, 1));
  }

  @Test
  void onlyAPostedJournalEntryMayBeRecorded() {
    Ledger ledger = new Ledger(LedgerId.generate(), ChartOfAccountsId.generate());
    JournalEntry unposted =
        new JournalEntry(
            JournalEntryId.generate(),
            ledger.id(),
            StandardJournalType.STANDARD,
            List.of(
                JournalLine.debit(
                    JournalLineId.generate(), AccountId.generate(), Money.of("10.00", USD)),
                JournalLine.credit(
                    JournalLineId.generate(), AccountId.generate(), Money.of("10.00", USD))));

    assertThatThrownBy(() -> ledger.recordJournalEntry(unposted))
        .isInstanceOf(IllegalStateException.class);

    unposted.validate();
    unposted.post();
    ledger.recordJournalEntry(unposted);

    assertThat(ledger.journalEntries()).containsExactly(unposted.id());
  }
}
