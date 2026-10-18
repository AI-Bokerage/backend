package io.genfin.ledger.ledger;

import io.genfin.api.domain.AggregateRoot;
import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.ledger.id.BalanceSnapshotId;
import io.genfin.ledger.id.ChartOfAccountsId;
import io.genfin.ledger.id.JournalEntryId;
import io.genfin.ledger.id.LedgerId;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.lifecycle.StandardLedgerStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ledger aggregate root. Owns its Chart of Accounts, posted Journal Entries, Balance Snapshots
 * and Accounting Periods only as identifiers at this stage — the detailed
 * account/journal/balance/period models arrive in later phases, this establishes the aggregate's
 * shape and invariants.
 *
 * <p>Mirrors {@code io.genfin.reconciliation.reconciliation.Reconciliation}: the ledger never owns
 * Invoice/Payment/Refund/Settlement/BankTransaction records directly, only generic {@code
 * Reference}s to them via the journal entries/facts posted into it.
 */
public final class Ledger extends AggregateRoot<LedgerId> {

  private final ChartOfAccountsId chartOfAccountsId;
  private final List<JournalEntryId> journalEntries = new ArrayList<>();
  private final List<BalanceSnapshotId> balanceSnapshots = new ArrayList<>();
  private final List<AccountingPeriodId> accountingPeriods = new ArrayList<>();
  private final List<String> history = new ArrayList<>();
  private final Map<String, String> metadata = new LinkedHashMap<>();

  public Ledger(LedgerId id, ChartOfAccountsId chartOfAccountsId) {
    super(id);
    this.chartOfAccountsId =
        Validate.notNull(chartOfAccountsId, "chartOfAccountsId must not be null.");
    this.history.add("OPENED");
  }

  public ChartOfAccountsId chartOfAccountsId() {
    return chartOfAccountsId;
  }

  public List<JournalEntryId> journalEntries() {
    return List.copyOf(journalEntries);
  }

  public List<BalanceSnapshotId> balanceSnapshots() {
    return List.copyOf(balanceSnapshots);
  }

  public List<AccountingPeriodId> accountingPeriods() {
    return List.copyOf(accountingPeriods);
  }

  public List<String> history() {
    return List.copyOf(history);
  }

  public Map<String, String> metadata() {
    return Map.copyOf(metadata);
  }

  public void recordJournalEntry(JournalEntryId journalEntryId) {
    journalEntries.add(Validate.notNull(journalEntryId, "journalEntryId must not be null."));
    history.add("JOURNAL_ENTRY_POSTED:" + journalEntryId.value());
  }

  /**
   * Records a {@link JournalEntry} into this ledger. Only an entry whose lifecycle has actually
   * reached {@link StandardLedgerStatus#POSTED} may be recorded - the ledger relies on the journal
   * entry's own SPI-driven lifecycle to guarantee this, rather than re-deriving posting rules
   * itself.
   */
  public void recordJournalEntry(JournalEntry journalEntry) {
    Validate.notNull(journalEntry, "journalEntry must not be null.");
    Validate.state(
        journalEntry.status() == StandardLedgerStatus.POSTED,
        "Only posted journal entries may be recorded in the ledger.");
    recordJournalEntry(journalEntry.id());
  }

  public void recordBalanceSnapshot(BalanceSnapshotId balanceSnapshotId) {
    balanceSnapshots.add(
        Validate.notNull(balanceSnapshotId, "balanceSnapshotId must not be null."));
  }

  public void openPeriod(AccountingPeriodId accountingPeriodId) {
    accountingPeriods.add(
        Validate.notNull(accountingPeriodId, "accountingPeriodId must not be null."));
    history.add("PERIOD_OPENED:" + accountingPeriodId.value());
  }

  public void putMetadata(String key, String value) {
    metadata.put(Validate.notBlank(key, "key must not be blank."), value);
  }

  public Summary summary() {
    return new Summary(journalEntries.size(), balanceSnapshots.size(), accountingPeriods.size());
  }

  /** A point-in-time count summary of this ledger. */
  public record Summary(int journalEntryCount, int balanceSnapshotCount, int periodCount)
      implements ValueObject {}
}
