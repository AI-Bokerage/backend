package io.genfin.ledger.port.trialbalance;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.period.AccountingPeriod;
import io.genfin.ledger.trialbalance.TrialBalance;
import io.genfin.money.currency.Currency;
import java.util.List;

/**
 * Derives a {@link TrialBalance} for one {@link AccountingPeriod} from a set of {@link
 * JournalEntry}s. Callers supply the entries that belong to {@code period}; Gen-Fin's ledger model
 * carries no period reference on {@link JournalEntry} itself, so which entries fall inside a given
 * period is left to whatever the application already uses to select them. Every entry is expected
 * to post in {@code currency} - a deployment posting in more than one currency runs one calculation
 * per currency.
 */
public interface TrialBalanceCalculator extends Extension {

  TrialBalance calculate(AccountingPeriod period, List<JournalEntry> entries, Currency currency);
}
