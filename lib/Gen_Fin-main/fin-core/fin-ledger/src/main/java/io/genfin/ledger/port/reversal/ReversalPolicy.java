package io.genfin.ledger.port.reversal;

import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.journal.JournalEntry;
import io.genfin.ledger.posting.PostingIssue;
import io.genfin.ledger.reversal.ReversalReason;
import java.util.Optional;

/**
 * Governs whether a {@link JournalEntry} may be reversed or adjusted for a given {@link
 * ReversalReason}, before {@link io.genfin.ledger.port.reversal.ReverseJournal} touches its
 * lifecycle. Mirrors {@code io.genfin.refund.port.policy.ReasonPolicy}.
 */
public interface ReversalPolicy extends Extension {

  /** Empty means the reversal/adjustment is permitted. */
  Optional<PostingIssue> check(JournalEntry original, ReversalReason reason);
}
