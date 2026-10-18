package io.genfin.ledger.ledger;

import io.genfin.ledger.id.AccountingPeriodId;
import io.genfin.ledger.id.BalanceSnapshotId;
import io.genfin.ledger.id.ChartOfAccountsId;
import io.genfin.ledger.id.JournalEntryId;
import io.genfin.ledger.id.LedgerId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link Ledger} aggregates. Preferred over the canonical constructor for readability at
 * call sites, and lets callers seed initial journal entries/balance snapshots/periods/metadata in
 * one expression instead of a constructor call followed by a run of {@code
 * recordJournalEntry}/{@code openPeriod} calls. Mirrors {@code
 * io.genfin.reconciliation.reconciliation.ReconciliationBuilder}.
 */
public final class LedgerBuilder {

  private LedgerId id;
  private ChartOfAccountsId chartOfAccountsId;
  private final List<JournalEntryId> journalEntries = new ArrayList<>();
  private final List<BalanceSnapshotId> balanceSnapshots = new ArrayList<>();
  private final List<AccountingPeriodId> accountingPeriods = new ArrayList<>();
  private final Map<String, String> metadata = new LinkedHashMap<>();

  private LedgerBuilder() {}

  public static LedgerBuilder newLedger() {
    return new LedgerBuilder();
  }

  public LedgerBuilder id(LedgerId id) {
    this.id = id;
    return this;
  }

  public LedgerBuilder chartOfAccountsId(ChartOfAccountsId chartOfAccountsId) {
    this.chartOfAccountsId = chartOfAccountsId;
    return this;
  }

  public LedgerBuilder journalEntry(JournalEntryId journalEntryId) {
    journalEntries.add(journalEntryId);
    return this;
  }

  public LedgerBuilder journalEntries(List<JournalEntryId> journalEntryIds) {
    journalEntries.addAll(journalEntryIds);
    return this;
  }

  public LedgerBuilder balanceSnapshot(BalanceSnapshotId balanceSnapshotId) {
    balanceSnapshots.add(balanceSnapshotId);
    return this;
  }

  public LedgerBuilder balanceSnapshots(List<BalanceSnapshotId> balanceSnapshotIds) {
    balanceSnapshots.addAll(balanceSnapshotIds);
    return this;
  }

  public LedgerBuilder accountingPeriod(AccountingPeriodId accountingPeriodId) {
    accountingPeriods.add(accountingPeriodId);
    return this;
  }

  public LedgerBuilder accountingPeriods(List<AccountingPeriodId> accountingPeriodIds) {
    accountingPeriods.addAll(accountingPeriodIds);
    return this;
  }

  public LedgerBuilder metadata(String key, String value) {
    metadata.put(key, value);
    return this;
  }

  public Ledger build() {
    Ledger ledger = new Ledger(id == null ? LedgerId.generate() : id, chartOfAccountsId);
    journalEntries.forEach(ledger::recordJournalEntry);
    balanceSnapshots.forEach(ledger::recordBalanceSnapshot);
    accountingPeriods.forEach(ledger::openPeriod);
    metadata.forEach(ledger::putMetadata);
    return ledger;
  }
}
