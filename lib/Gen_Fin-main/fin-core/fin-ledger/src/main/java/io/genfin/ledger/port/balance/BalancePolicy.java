package io.genfin.ledger.port.balance;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.journal.JournalEntry;

/**
 * Decides which {@link JournalEntry}s a {@link BalanceCalculator} rolls into a balance. Gen-Fin
 * hardcodes no such rule - a deployment may, for instance, choose to include entries pending
 * settlement or exclude archived ones - so this is an SPI extension point, not a fixed status
 * filter, mirroring {@code io.genfin.reconciliation.port.matching.MatchingPolicy}.
 */
public interface BalancePolicy extends Extension {

  boolean includes(JournalEntry entry);
}
