package io.genfin.ledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.ledger.id.BalanceSnapshotId;
import io.genfin.ledger.id.ChartOfAccountsId;
import io.genfin.ledger.id.JournalEntryId;
import io.genfin.ledger.id.LedgerId;
import org.junit.jupiter.api.Test;

class LedgerBuilderTest {

  @Test
  void buildsALedgerWithNoSeedingBeyondItsChartOfAccounts() {
    ChartOfAccountsId chartOfAccountsId = ChartOfAccountsId.generate();

    Ledger ledger = LedgerBuilder.newLedger().chartOfAccountsId(chartOfAccountsId).build();

    assertThat(ledger.id()).isNotNull();
    assertThat(ledger.chartOfAccountsId()).isEqualTo(chartOfAccountsId);
    assertThat(ledger.summary()).isEqualTo(new Ledger.Summary(0, 0, 0));
  }

  @Test
  void seedsJournalEntriesBalanceSnapshotsPeriodsAndMetadataInOneExpression() {
    LedgerId id = LedgerId.generate();
    JournalEntryId journalEntryId = JournalEntryId.generate();
    BalanceSnapshotId balanceSnapshotId = BalanceSnapshotId.generate();
    AccountingPeriodId accountingPeriodId = AccountingPeriodId.generate();

    Ledger ledger =
        LedgerBuilder.newLedger()
            .id(id)
            .chartOfAccountsId(ChartOfAccountsId.generate())
            .journalEntry(journalEntryId)
            .balanceSnapshot(balanceSnapshotId)
            .accountingPeriod(accountingPeriodId)
            .metadata("k", "v")
            .build();

    assertThat(ledger.id()).isEqualTo(id);
    assertThat(ledger.journalEntries()).containsExactly(journalEntryId);
    assertThat(ledger.balanceSnapshots()).containsExactly(balanceSnapshotId);
    assertThat(ledger.accountingPeriods()).containsExactly(accountingPeriodId);
    assertThat(ledger.metadata()).containsEntry("k", "v");
  }
}
